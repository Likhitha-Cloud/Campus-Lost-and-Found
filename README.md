# Campus Lost & Found Management System

A Java application where students report lost and found items on campus. It is built for **CSE 213 - Advanced Java Programming**.

## Features
- Report LOST and FOUND items, stored permanently with JDBC (SQLite)
- Search by item name or location
- Automatic matching of lost and found items (Java Streams)
- **Hidden question:** the finder's contact is shown only after the claimant answers a secret question correctly
- Mark items as resolved, or delete them
- Two interfaces: Swing desktop app and a browser version using Java's built-in web server

## Java concepts used
OOP (classes, encapsulation, inheritance, polymorphism, abstraction, interfaces), exception handling, multithreading, collections, generics, streams, Swing, JDBC.

## How to run
Requires JDK 17 or newer. Open a Command Prompt in the project folder:

```
javac -cp "lib/*" -d out src\*.java
java -cp "out;lib/*" MainApp
```

For the web version:

```
java -cp "out;lib/*" WebApp
```

Then open http://localhost:8080 in a browser.

## Project structure
- `src/` - all source code
- `lib/` - SQLite JDBC driver and slf4j jars

## Team
[Name 1 - Reg. No.], [Name 2 - Reg. No.], [Name 3 - Reg. No.]
Faculty: [Faculty name]
