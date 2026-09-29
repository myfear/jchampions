package org.acme.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

@QuarkusMain(name = "cards")
public class CardsMain implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Inject
    @TopCommand
    CardsCommand command;

    @Override
    public int run(String... args) {
        return new CommandLine(command, factory).execute(args);
    }
}
