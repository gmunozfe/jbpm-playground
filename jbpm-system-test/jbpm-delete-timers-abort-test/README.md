Timer not deleted at process instance abort
===========================================

This project is used as a reproducer to validate fix provided by [jbpm#2154](https://github.com/kiegroup/jbpm/pull/2154) 

[JBPM-10087] Timer not deleted at process instance abort

## Building

For building this project locally, you firstly need to have the following tools installed locally:
- git client
- Java 1.8
- Maven
- docker (because of testcontainers makes use of it).

Once you cloned the repository locally all you need to do is execute the following Maven build:

```
mvn clean install
```

Other options are:

Property      | Function
------------- | ----------------------------------------------
noPatch       | If present, skip the installation of the patch


Example:

```
mvn clean install -Pfull -DnoPatch
```

