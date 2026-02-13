# Method-Centric Context Construction Using Call Graphs

## Overview

This project builds method-centric context from Java code using call graph analysis with SootUp. It targets research workflows that need method bodies, callers/callees, and Javadocs for LLM data generation.

Key goals:
- Build call graphs (CHA; RTA optional)
- Extract method bodies, call relationships, and Javadocs
- Produce JSON/text artifacts for downstream analysis

---

## Features

- Call graph construction (CHA; RTA stubbed)
- Single-method focused context extraction
- Method body and Javadoc extraction from source
- CSV-driven target method selection
- Text outputs for call graphs and method context

---

## Requirements

- Java 17+
- Maven 3.6+
- SootUp (included under [SootUp/](SootUp/))

---

## Project Layout

```
.
├── src/main/java/BuildCG.java
├── commons-lang3-3.12.0-src/         # Example project + inputs_with_id.csv
├── ExampleToTest/                      # Small examples
├── CurrentAnalyzingCompiled/           # Optional compiled classes
├── Output_CallGraph.txt
├── Output_CallGraph_CHA.txt
├── Output_Single_Method_CallGraph.txt
├── Output_Single_Method_BodySourceCode.txt
├── Output_Single_Method_Javadocs.txt
├── method_context_hashCode.json
├── pom.xml
└── run.sh
```

---

## Quick Start

1) Build the project:
```bash
mvn clean compile
```

2) Run the analysis:
```bash
mvn exec:java
```

You can also use the helper script:
```bash
./run.sh
```

---

## Configure the Target Method

The current entry point is [src/main/java/BuildCG.java](src/main/java/BuildCG.java). The tool reads a single row by ID from `commons-lang3-3.12.0-src/inputs_with_id.csv` and uses that to locate the method.

Update the following items in `BuildCG.java` as needed:
- Project classpath (`projectPath`)
- CSV row ID (`loadRowById_OpenCSV("...")`)
- Target class name (currently hardcoded)

---

## Outputs

- `Output_CallGraph.txt`: full call graph
- `Output_CallGraph_CHA.txt`: CHA call graph dump
- `Output_Single_Method_CallGraph.txt`: callers/callees for the target method
- `Output_Single_Method_BodySourceCode.txt`: extracted method bodies
- `Output_Single_Method_Javadocs.txt`: extracted Javadocs
- `method_context_hashCode.json`: sample JSON context output

---

## Notes

- The current implementation reads a single CSV row by ID. Extending it to iterate all rows is straightforward and planned.
- Line number normalization is handled in `normalizeSignatureLine` to improve signature detection near comments.

---

## References

- SootUp: https://soot-oss.github.io/soot/
