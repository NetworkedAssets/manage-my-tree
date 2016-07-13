package com.networkedassets.plugins.managemytree.opml

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OPML")
data class Opml(val head: OpmlHead, val body: OpmlBody) {
    constructor(): this(OpmlHead(), OpmlBody())
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Head")
data class OpmlHead(val title: String?) {
    constructor(): this("")
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Body")
data class OpmlBody(val outline: List<OpmlOutline>) {
    constructor(): this(arrayListOf())
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Outline")
data class OpmlOutline(
        val outline: List<OpmlOutline>,
        @XmlAttribute(name = "text", required = false) val text: String?,
        @XmlAttribute(name = "title", required = true) val title: String
) {
    constructor(): this(arrayListOf(), "", "")
}