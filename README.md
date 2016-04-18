# boilersuit-core
Boilersuit engine for data analysis and transformation by Brickwork Ventures.

<b>CAUTION</b>
This is used by developers who want to build an application based on the BS engine; if you simple want to use the tool (has the engine integrated of course), please visit <a href="https://github.com/BrickworkVentures/boilersuit-light">boilersuit-light</a>.
For general information, please visit the <a href="https://github.com/BrickworkVentures/boilersuit-light">boilersuit-light</a> page.

# Getting Started
## Build
###1. Get a clone:
```
git clone https://github.com/BrickworkVentures/boilersuit-core.git
```
###2. Get Maven and assemble it
```
mvn compile assembly:single
```

# Using the man tool
To create a html doc directly out of the class comments in the XXXInterpreter classes:
## Arguments
```
bsman src/main/java/ch/brickwork/bsuit/interpreter/interpreters/ src/main/doc/interpreters.html
```