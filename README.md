# tomcat-valve-request-filter

This repository contains a maven project with a Tomcat Valve class that filters HTTP requirements 
based on a list of regular expressions.

This project is inspired on the [Tomcat-Valve-Example][1] by Keet Sugathadasa.

Version 0.1 of the valve has been developed and tested on JDK 8 and Tomcat 9.0.
Version 1.0 of the valve has been developed and tested on JDK 11 and Tomcat 10.1.

# Integration

This code is available at Maven Central

```xml
<dependency>
    <groupId>codes.vps</groupId>
    <artifactId>tomcat-valve-request-filter</artifactId>
    <version>1.0</version>
</dependency>
```

The valve has no additional runtime dependencies.

# Configuration

To use this valve, the tomcat-valve-request-filter jar file, either compiled locally
or downloaded from Maven Central, must be placed in the Tomcat's lib directory,
and it must be declared in the Tomcat configuration file (typically `conf/server.xml`),
inside the corresponding `Connector` definition.

The valve is defined by the className attribute with the value 
`org.excelfore.tomcat.valve.acl.TomcatValve`.

Each valve is to be configured by a configuration file (those can be reused for multiple valves),
specified in the `config` attribute of the valve declaration.

## Valve Example

```xml
<Valve className="org.excelfore.tomcat.valve.acl.TomcatValve" config="conf/my-config.xml"/>
```

## Valve Configuration

The valve configuration file must adhere to the schema document available in `tomcat-valve-request.xsd`
file in the distributed JAR.

The configuration establishes:

1. The default behavior of handling the request if none of the specified filters match.
2. List of zero or more rules that contain patters for matching the URIs of the incoming requests, and the outcome to apply if the rule matches.

XSD provides documentation information for the allowed content.

## Example valve configuration:

The example below sets up a valve to:
1. Requests to URI `/good` are passed through
2. Requests to URL `/bad` are returned with 404
3. Other requests are returned with 400

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<d:tomcat-valve-request
        xmlns:d="https://dev-esync.excelfore.com/schema/public/xsd/1.0/tomcat-valve-request.xsd"
        xmlns:x="http://www.w3.org/2001/XMLSchema-instance" asDebug="true">
    <d:outcome x:type="d:SendError" code="400"/>

    <d:match pattern="\/good">
        <d:outcome x:type="d:Continue"/>
    </d:match>

    <d:match pattern="\/bad">
        <d:outcome x:type="d:SendError" code="404"/>
    </d:match>

</d:tomcat-valve-request>
```

# How this works

When the invoke() method is called, the valve attempts to match the request against a regular expression pattern defined in the urlPatterns attribute.

If none match, the valve returns an HTTP 404 error.

[1]: https://github.com/Keetmalin/Tomcat-Valve-Example
