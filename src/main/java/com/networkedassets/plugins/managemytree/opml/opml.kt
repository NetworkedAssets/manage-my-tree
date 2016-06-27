package com.networkedassets.plugins.managemytree.opml

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OPML")
data class Opml(val head: Head, val body: Body) {
    constructor(): this(Head(), Body())
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Head")
data class Head(val title: String?) {
    constructor(): this("")
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Body")
data class Body(val outline: List<Outline>) {
    constructor(): this(arrayListOf())
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Outline")
data class Outline(
        val outline: List<Outline>,
        @XmlAttribute(name = "text", required = false) val text: String?,
        @XmlAttribute(name = "title", required = true) val title: String
) {
    constructor(): this(arrayListOf(), "", "")
}