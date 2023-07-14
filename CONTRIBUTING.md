# 📖 Guidelines

- If the existing tests do not already cover your changes, please add tests.
- Pull requests should be created on the _main_ branch.

# ⚒️ Building and Testing the Sources

## Build Maven Dependency Tree

Clone the sources and CD to the root directory of the project:

```
git clone https://github.com/jfrog/maven-dep-tree.git
cd maven-dep-tree
```

Build the sources as follows:

On Unix based systems run:

```
maven clean package
```

Once completed, you'll find the maven-dep-tree.jar at the target/ directory.

## Tests

To run the tests, run the following command:

```
mvn clean verify -DskipITs=false
```