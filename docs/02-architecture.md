# 02 — How this project is put together

## What you are looking at

A **proof of concept**, not a production payment engine.

| It does | It does not |
|---------|-------------|
| Generate JAXB classes from the ESB XSD using the **Gradle XJC plugin** | Call the real Consorsbank ESB |
| Compile those classes with **Java 21** and **Spring Boot 4.0.8** | Generate full SOAP client stubs |
| Expose REST JSON so you can prove the classes work | Send or receive a SOAP envelope on the wire |
| Mock approve/decline using the generated types | Persist anything or talk to a database |

Package: `de.consorsbank.consumingwebservice` (the original Spring “consuming a web service” sample shape).

---

## Mental model: two worlds in one process

```mermaid
flowchart LR
    subgraph modern ["Modern face"]
      C[curl / Postman<br/>JSON over HTTP]
      R[AuthorizationController]
    end
    subgraph generated ["Legacy contract, as Java"]
      REQ[TransactionAuthorizationRequestType]
      RES[TransactionAuthorizationResponseType]
      ENUM[AuthorizationStatusType]
    end
    subgraph logic ["Your code"]
      S[AuthorizationService]
    end
    C --> R --> S
    S --> REQ
    S --> RES
    S --> ENUM
```

The controller speaks **JSON** because that is easy to demo. The service speaks **JAXB types** because that is what a real SOAP integration would use before marshalling to XML.

In production you would typically:

1. Build `TransactionAuthorizationRequestType`
2. `JAXBContext` marshal it into XML
3. Wrap it in a SOAP envelope
4. POST to the ESB URL from the WSDL
5. Unmarshal the XML response back into `TransactionAuthorizationResponseType`

This POC skips steps 2–4 and invents the response in Java. That is enough to prove generation + compilation + runtime.

---

## Folder map

```
Springboot-JAXB-POC/
├── wsdl/
│   ├── ESBTransactionAuthorization_v1_0.wsdl   ← full dummy contract (documentation + future SOAP)
│   └── ESBTransactionAuthorization_v1_0.xsd    ← extracted schema; THIS is XJC input
├── src/main/java/.../
│   ├── Application.java                        ← Spring Boot entry
│   ├── controller/AuthorizationController.java ← REST
│   └── service/AuthorizationService.java       ← uses generated types
├── src/main/resources/application.yml          ← port 8080
├── src/test/java/.../ApplicationTest.java
├── build.gradle                                ← XJC plugin config lives here
└── build/generated-sources/jaxb/...            ← created at build time, do not edit
```

Generated sources are **not** committed as the source of truth. They appear after `./gradlew genJaxb` or any compile. Treat them like “compiler output you can read”.

---

## The Gradle XJC generation path (the important bit)

The current build applies `com.github.bjornvester.xjc` and configures XJC directly:

```groovy
plugins {
    id 'com.github.bjornvester.xjc' version '1.9.1'
}

xjc {
    xsdDir.set(layout.projectDirectory.dir('wsdl'))
    includes.set(['ESBTransactionAuthorization_v1_0.xsd'])
    outputJavaDir.set(layout.buildDirectory.dir('generated-sources/jaxb'))
    useJakarta.set(true)
    xjcVersion.set('4.0.9')
    defaultPackage.set('de.consorsbank.consumingwebservice.wsdl')
    addCompilationDependencies.set(false)
    options.add('-extension')
}
```

The plugin registers XJC as a Gradle generation task, integrates generated Java into the main source set, and connects generation to compilation. The project also keeps a `genJaxb` alias for the convenient command `./gradlew genJaxb`.

```mermaid
sequenceDiagram
    participant You
    participant Gradle
    participant Plugin as XJC Gradle plugin
    participant XJC as XJC 4.0.9
    participant Disk

    You->>Gradle: ./gradlew bootRun
    Gradle->>Plugin: execute XJC task before Java compilation
    Plugin->>XJC: XSD + package + Jakarta options
    XJC->>Disk: build/generated-sources/jaxb/**/*.java
    Gradle->>Gradle: compileJava (hand-written + generated)
    Gradle->>You: app on :8080
```

Why use the Gradle plugin instead of AntBuilder?

- The plugin manages XJC's generator classpath and task integration.
- Gradle knows the generation task's inputs/outputs and orders it before compilation.
- The build no longer manually registers `com.sun.tools.xjc.XJCTask` with `ant.taskdef`.
- The plugin is a community dependency; the migration note documents this maintenance trade-off.

Key `build.gradle` settings:

| Setting | Value | Why |
|---------|--------|-----|
| `xsdDir` + `includes` | `wsdl/ESBTransactionAuthorization_v1_0.xsd` | XJC processes the standalone schema, not the full WSDL |
| `outputJavaDir` | `build/generated-sources/jaxb` | Preserve the generated-source output path |
| `defaultPackage` | `de.consorsbank.consumingwebservice.wsdl` | Stable import path for application code |
| `useJakarta` / `xjcVersion` | `true` / `4.0.9` | Generate Jakarta JAXB types with aligned XJC |
| `options.add('-extension')` | XJC extension mode | Preserve the previous generator option |

The XJC plugin integrates the generated directory into Java compilation; IntelliJ imports the source directory through the Gradle model.

---

## Runtime logic (the mock “ESB”)

`AuthorizationService.authorize(...)`:

1. New `TransactionAuthorizationRequestType`
2. Fill `transactionId` (UUID), account, amount, currency, XML timestamp
3. New `TransactionAuthorizationResponseType` with the same id
4. **If amount > 10_000 → `DECLINED`** with error text  
   **Else → `APPROVED`** with `AUTH-xxxxxxxx`
5. Return the JAXB response object

The controller copies fields into a `Map` so Jackson can serialize JSON **without** a JAXB module. That is a demo shortcut. A SOAP client would marshal the object to XML instead.

```mermaid
flowchart TD
    A["POST /api/authorize JSON"] --> B["Parse accountNumber, amount, currency"]
    B --> C["Fill TransactionAuthorizationRequestType"]
    C --> D{"amount > 10000?"}
    D -->|yes| E["Status DECLINED<br/>errorMessage set"]
    D -->|no| F["Status APPROVED<br/>authorizationCode AUTH-..."]
    E --> G["Map → JSON response"]
    F --> G
```

`GET /api/health` only does `new TransactionAuthorizationRequestType()`. If that class is missing from the classpath, the app would not even compile — so a 200 here is a cheap runtime proof.

---

## XSD → Java mapping in this repo

| XSD | Java |
|-----|------|
| `complexType TransactionAuthorizationRequestType` | class `TransactionAuthorizationRequestType` |
| `element TransactionAuthorizationRequest` | `ObjectFactory.createTransactionAuthorizationRequest(...)` |
| `simpleType AuthorizationStatusType` enumerations | enum `AuthorizationStatusType` |
| `xsd:decimal` | `java.math.BigDecimal` |
| `xsd:dateTime` | `javax.xml.datatype.XMLGregorianCalendar` |
| `xsd:string` | `String` |
| `CurrencyCodeType` pattern `[A-Z]{3}` | still a `String` (XJC does not always generate a dedicated type for simple restrictions) |

`XMLGregorianCalendar` looks alien if you only know `Instant`. It exists because XML Schema date/time is not the same as `java.time`. The service converts `ZonedDateTime.now()` through `DatatypeFactory`.

---

## Why not call this a SOAP client?

Because these pieces are **missing**:

- HTTP call to the WSDL `soap:address`
- SOAP envelope wrap/unwrap
- `AuthorizeTransaction` port interface
- Fault mapping to `AuthorizationFaultType` on the wire

Those come from **JAX-WS / CXF**, not from JAXB alone. JAXB is the **payload** layer. JAX-WS is the **phone call** layer.

```mermaid
flowchart TB
    subgraph thisPoc ["This POC"]
      XSD --> XJC --> POJO["JAXB POJOs"]
      POJO --> Mock["In-memory approve/decline"]
      Mock --> REST
    end
    subgraph fullStack ["A full SOAP client would add"]
      WSDL --> WSDL2Java["wsimport / CXF"]
      WSDL2Java --> Port["Port interface + stubs"]
      POJO --> Marshal["JAXB marshal XML"]
      Port --> SOAP["HTTP + SOAP envelope"]
      Marshal --> SOAP
    end
```

---

## Versions (so you do not mix eras)

| Piece | Version | Era it belongs to |
|-------|---------|-------------------|
| Java toolchain | 21 | Current LTS |
| Spring Boot | 4.0.8 | Jakarta EE, Jackson 3, modular starters |
| Gradle wrapper | 8.14.6 | Boot 4 needs Gradle 8.14+ or 9.x |
| `jakarta.xml.bind-api` | 4.0.5 | Jakarta EE 10 (Boot BOM) |
| `jaxb-runtime` / `jaxb-xjc` | 4.0.9 | GlassFish JAXB RI (Boot BOM) |
| XJC Gradle plugin | 1.9.1 | Build-time schema-to-Java integration |

If someone adds `javax.xml.bind:jaxb-api` “because a blog from 2017 said so”, the generator can fail. That is why `build.gradle` excludes it.

Next: [03 — How to run](03-how-to-run.md).
