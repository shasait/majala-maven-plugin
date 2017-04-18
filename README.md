# Maven Plugin - MaJaLa (Maven Java Launcher)

## Purpose

Launch an java application by only specifying GAV and MainClass.
In contrast to Maven Exec Plugin it does not need a Maven Project in the current working directory.

## Example

    mvn de.hasait.majala:majala-maven-plugin:majala \
    -Dmajala.coords=org.hsqldb:hsqldb:2.2.9 \
    -Dmajala.mainClass=org.hsqldb.Server \
    -Dmajala.args="-database.0 mem:. -dbname.0 jtrain"

## License
MaJaLa is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
