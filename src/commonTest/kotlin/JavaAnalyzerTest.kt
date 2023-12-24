import kotlin.test.Test
import kotlin.test.assertTrue

/** Tests the [JavaAnalyzer] class. */
class JavaAnalyzerTest {

    @Test
    fun testJavaInfo() {
        assertTrue(true)
        // TODO: Test the JavaAnalyzer using the .info files in src/commonTest/resources.
    }
}

/*
# Distribution       | IMPLEMENTOR              | IMPLEMENTOR_VERSION prefix             | root folder slug
# -------------------| -------------------------|----------------------------------------|-----------------
# AdoptOpenJDK       | AdoptOpenJDK             | AdoptOpenJDK                           | adopt
# Alibaba Dragonwell | Alibaba                  | (Alibaba Dragonwell Extended Edition)* | dragonwell
# Amazon Corretto    | Amazon.com Inc.*         | Corretto*                              | corretto or amazon-corretto
# Azul Zulu          | Azul Systems, Inc.*      | Zulu*                                  | zulu
# BellSoft Liberica  | BellSoft                 | <none>                                 | <none>
# Eclipse Temurin    | Eclipse Adoptium         | Temurin                                | <none>
# IBM Semuru         | IBM Corporation          | <none>                                 | <none>
# JetBrains JBRSDK   | N/A or JetBrains s.r.o.* | JBRSDK*                                | jbrsdk*
# Microsoft OpenJDK  | Microsoft                | Microsoft                              | <none>
# OpenLogic OpenJDK  | OpenLogic                | OpenLogic-OpenJDK                      | openlogic-openjdk
# GraalVM Community  | GraalVM Community        | <none>                                 | graalvm-ce or graalvm-community-openjdk
# GraalVM Enterprise | Oracle Corporation       | <none>                                 | graalvm-jdk
# Oracle Java SE     | Oracle Corporation       | <none>                                 | oracle*
# Oracle OpenJDK     | Oracle Corporation       | <none>                                 | oracle*
# SAP SapMachine     | SAP SE                   | SapMachine                             | sapmachine-jdk
# Tencent KonaJDK    | Tencent*                 | TencentKonaJDK*                        | TencentKona
# Ubuntu OpenJDK     | Ubuntu or Private Build  | <none>                                 | <none>
*/
