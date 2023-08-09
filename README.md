# tomcat-valve-request-filter

This repository contains a maven project with a Tomcat Valve class that filters HTTP requirements 
based on a list of regular expressions.

This project is inspired on the [Tomcat-Valve-Example][1] by Keet Sugathadasa.

The valve has been developed and tested on Tomcat 9.

# Integration

This code is available at Maven Central

```xml
<dependency>
    <groupId>codes.vps</groupId>
    <artifactId>tomcat-valve-request-filter</artifactId>
    <version>0.1</version>
</dependency>
```

# Configuration

To use this valve, the tomcat-valve-request-filter jar file generated, must be located in the tomcat lib directory
and the valve definition with the regex list must be included in the server.xml configuration file.

The valve is defined by the className attribute with the value "org.excelfore.tomcat.TomcatValve".

Regex are defined using the urlPatterns attribute as a comma-separated list of strings.

# Example

```xml
<Valve 
        className="org.excelfore.tomcat.TomcatValve"
        urlPatterns="/path1/.*,/path2/\d+/.*" />
```

# How this works

When the invoke() method is called, the valve attempts to match the request against a regular expression pattern defined in the urlPatterns attribute.

If none match, the valve returns an HTTP 404 error.

# To use this project

**Step 1**: Clone this project.

**Step 2**: Open a terminal, and run `mvn install`. Now in the `target/` folder, a jar file should have been built.

**Step 3**: Copy this jar to `${TOMCAT_HOME}/lib` or `${CATALINA_HOME}/lib` folder. This is usually in `/usr/local/apache-tomcat9/lib`.

**Step 4**: Go to `${TOMCAT_HOME}/conf` or `${CATALINA_HOME}/conf` folder and add the following line to `server.xml`:

    <Valve className="org.excelfore.tomcat.TomcatValve" urlPatterns="..."/>

**Step 5**: Replace the "..." with yours specific regex list (comma-separated)

**Step 6**: Go to `${TOMCAT_HOME}/bin` or `${CATALINA_HOME}/bin` folder and run the following command in a terminal:

    ./catalina.sh run

**Step 7**: Next, your Tomcat server will start. Open a browser and go to `http://localhost:8080/` using both defined or not defined regex.

You will see HTTP 404 error if the requested path is not listed as a regex in the urlPatterns attribute.

Hope it helps!

[1]: https://github.com/Keetmalin/Tomcat-Valve-Example
