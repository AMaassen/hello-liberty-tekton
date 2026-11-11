package demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Draait in de Tekton-task 'maven-build'. Laat hem falen om te zien dat de pipeline stopt. */
class HelloResourceTest {

    @Test
    void geeftBegroeting() {
        assertTrue(new HelloResource().hello().startsWith(HelloResource.MESSAGE));
    }
}
