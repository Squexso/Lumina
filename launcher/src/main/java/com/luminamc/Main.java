package com.luminamc;

import javafx.application.Application;

/**
 * Plain (non-{@link javafx.application.Application}) entry point. Launching the
 * Application subclass indirectly avoids the "JavaFX runtime components are
 * missing" error when the modules are on the classpath rather than module path.
 */
public final class Main {

    public static void main(String[] args) {
        Application.launch(LuminaApp.class, args);
    }
}
