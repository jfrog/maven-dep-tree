[![Scanned by Frogbot](https://raw.github.com/jfrog/frogbot/master/images/frogbot-badge.svg)](https://github.com/jfrog/frogbot#readme)
[![Test](https://github.com/jfrog/maven-dep-tree/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/jfrog/maven-dep-tree/actions/workflows/test.yml)

# 🪶 Maven Dependency Tree

This Maven plugin reads the Maven dependencies of a given Maven project, and generates a dependency tree.
This package was developed by JFrog, and is used by [JFrog Frogbot](https://github.com/jfrog/frogbot)
to generate the dependency tree for projects using Maven dependencies.

## Table of Contents

- [Usage](#-usage)
    - [Tree](#-tree)
        - [Output](#output)
        - [Output Tree Structure](#output-tree-structure)
    - [Project Info](#-project-info)
        - [Output](#output-1)
- [Contributions](#-contributions)

## 🖥️ Usage

### 🌲 Tree

Run *tree* in a directory containing a pom.xml file. The plugin will generate a dependency tree for each subproject that
contains a pom.xml file.

The command:

```bash
mvn com.jfrog:maven-dep-tree:tree -DdepsTreeOutputFile=<path/to/output/file>
```

#### Output:

```
<path/to/dependency/tree1>
<path/to/dependency/tree2>
...
```

#### Output Tree Structure:

```json
{
  "root": "org.jfrog.test:multi:3.7-SNAPSHOT",
  "nodes": {
    "junit:junit:3.8.1": {
      "children": [],
      "configurations": [
        "test"
      ],
      "types": [
        "jar"
      ]
    },
    "org.jfrog.test:multi:3.7-SNAPSHOT": {
      "children": [
        "junit:junit:3.8.1"
      ],
      "configurations": [],
      "types": [
        "pom"
      ]
    }
  }
}
```

### 🧐 Project Info

Run *projects* in a directory containing a pom.xml file. The plugin will generate the project info for each subproject
that contains a pom.xml file.

The command:

```bash
mvn com.jfrog:maven-dep-tree:projects -q 
```

#### Output:

```sh
{"gav":"org.jfrog.test:multi:3.7-SNAPSHOT","parentGav":"","pomPath":"/path/to/maven-example/pom.xml"}
{"gav":"org.jfrog.test:multi1:3.7-SNAPSHOT","parentGav":"org.jfrog.test:multi:3.7-SNAPSHOT","pomPath":"/path/to/maven-example/multi1/pom.xml"}
{"gav":"org.jfrog.test:multi2:3.7-SNAPSHOT","parentGav":"org.jfrog.test:multi:3.7-SNAPSHOT","pomPath":"/path/to/maven-example/multi2/pom.xml"}
{"gav":"org.jfrog.test:multi3:3.7-SNAPSHOT","parentGav":"org.jfrog.test:multi:3.7-SNAPSHOT","pomPath":"/path/to/maven-example/multi3/pom.xml"}
```

## 💻 Contributions

We welcome pull requests from the community. To help us improve this project, please read
our [contribution](./CONTRIBUTING.md#-guidelines) guide.
