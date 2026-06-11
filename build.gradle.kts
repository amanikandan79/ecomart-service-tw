plugins {
    // JaCoCo for coverage reporting
    id("jacoco")

    java
    idea
    eclipse
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.3"
}

java {
     toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        println("java 21")
    } 
}

repositories {
    mavenCentral()
}

sourceSets {
    create("functionalTest") {
        java {
            compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
            runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
            setSrcDirs(listOf("src/functional-test"))
        }
    }
}

val functionalTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}
val functionalTestRuntimeOnly: Configuration by configurations.getting

configurations {
    configurations["functionalTestImplementation"].extendsFrom(configurations.testImplementation.get())
    configurations["functionalTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())
}


val functionalTest = task<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"

    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    shouldRunAfter("test")

    useJUnitPlatform()

    testLogging {
        events ("failed", "passed", "skipped", "standard_out")
    }
}


dependencies {
    /* Spring Boot */
    implementation ("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude (group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    testLogging {
        events ("failed", "passed", "skipped", "standard_out")
    }
}

// Configure JaCoCo
jacoco {
    toolVersion = "0.8.10"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files("build/classes/java/main"))
}

tasks.check { dependsOn(functionalTest) }
