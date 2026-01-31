package com.example.abxoverflow.droppedapk.utils

import android.util.Xml
import me.timschneeberger.reflectionexplorer.utils.cast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets


fun abx2xml(abxData: ByteArray): String {
    val `in`: XmlPullParser = Xml::class.java
        .getMethod("newBinaryPullParser")
        .invoke(null)!!.cast<XmlPullParser>()
    val out: XmlSerializer = Xml.newSerializer()

    try {
        val os = ByteArrayOutputStream()
        `in`.setInput(ByteArrayInputStream(abxData), StandardCharsets.UTF_8.name())
        out.setOutput(os, StandardCharsets.UTF_8.name())
        out.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        Xml::class.java
            .getMethod("copy", XmlPullParser::class.java, XmlSerializer::class.java)
            .invoke(null, `in`, out)

        out.flush()
        return os.toString(StandardCharsets.UTF_8.name())
    } catch (e: Exception) {
        throw IllegalStateException(e)
    }
}
