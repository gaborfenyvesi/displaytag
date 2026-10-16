/*
 * SPDX-License-Identifier: MIT
 * See LICENSE file for details.
 *
 * Copyright 2002-2026 Fabrizio Giustina, the Displaytag team
 */
package org.displaytag;

public class FontSettings {

    private final String fontsFolder;

    private final String fontsFileRegular;

    private final String fontsFileBold;

    private final String fontsFileRegularFallback;

    private final String fontsFileBoldFallback;

    public String getFontsFolder() {
        return fontsFolder;
    }

    public String getFontsFileRegular() {
        return fontsFileRegular;
    }

    public String getFontsFileBold() {
        return fontsFileBold;
    }

    public String getFontsFileRegularFallback() {
        return fontsFileRegularFallback;
    }

    public String getFontsFileBoldFallback() {
        return fontsFileBoldFallback;
    }

    public FontSettings(String fontsFolder, String fontsFileRegular, String fontsFileBold,
            String fontsFileRegularFallback, String fontsFileBoldFallback) {
        this.fontsFolder = fontsFolder;
        this.fontsFileRegular = fontsFileRegular;
        this.fontsFileBold = fontsFileBold;
        this.fontsFileRegularFallback = fontsFileRegularFallback;
        this.fontsFileBoldFallback = fontsFileBoldFallback;
    }

}
