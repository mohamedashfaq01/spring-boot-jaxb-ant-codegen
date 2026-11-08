# Spring Boot JAXB POC — Documentation

This folder explains the project in plain English for a mid-level developer who already knows Java and REST, but may not have lived through SOAP / WSDL / XSD / JAXB / Ant.

Start here, then open the pages in order. If you learn better by clicking than by reading, open the interactive guide first.

| Page | What you get |
|------|----------------|
| [01 — Concepts](01-concepts.md) | What SOAP, WSDL, XSD, JAXB, and Ant are, when they were popular, and whether people still use them |
| [02 — Architecture](02-architecture.md) | How *this* repo wires those ideas together, including the Gradle → Ant → XJC path |
| [03 — How to run](03-how-to-run.md) | Prerequisites, commands, expected JSON input/output, mock business rules |
| [Interactive guide](interactive-guide.html) | Clickable diagrams, a SOAP envelope walkthrough, and a live mock of `/api/authorize` |

Open the interactive guide in a browser:

```bash
open docs/interactive-guide.html
```

## One-sentence summary of the project

This is a **Spring Boot 3 / Java 21** app that **does not call a real SOAP server**. It proves you can take a bank-style **WSDL/XSD contract**, generate Java classes with **JAXB XJC through Apache Ant** (the same pattern older enterprise builds used), compile those classes into a modern Spring app, and expose a **JSON REST API** that uses those generated types.

## What problem it solves

Banks and large enterprises still publish SOAP services. New teams often speak JSON/REST. The gap is:

1. The contract lives in **XML Schema** (XSD), often wrapped in a **WSDL**.
2. You need **Java classes** that match that contract exactly (field names, types, enums).
3. Legacy shops generated those classes with **Ant + XJC**.
4. You still want to run that generation inside **Gradle** on **Java 21**.

This POC is the smallest working answer to: *“Can we keep the old Ant/XJC generation style, but consume the generated models from Spring Boot?”*

It is **not** a full SOAP client. Full SOAP stubs would need `wsimport` / Apache CXF `wsdl2java`. Here we only generate the **data classes**.
