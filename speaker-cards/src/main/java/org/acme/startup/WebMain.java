package org.acme.startup;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/** Keep the normal package as the web application; the cards profile selects CardsMain. */
@QuarkusMain
public class WebMain implements QuarkusApplication {
    @Override
    public int run(String... args) {
        Quarkus.waitForExit();
        return 0;
    }
}
