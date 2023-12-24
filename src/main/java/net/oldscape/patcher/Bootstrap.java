package net.oldscape.patcher;

import joptsimple.OptionParser;
import joptsimple.ValueConverter;

import java.io.IOException;
import java.nio.file.Path;

public class Bootstrap {

    public static void main(String[] args) throws IOException, InterruptedException {
        var options = parseOptions(args);
        var patcher = Patcher.create(options);
        patcher.process();
    }

    private static PatcherOptions parseOptions(String[] args) {
        var parser = new OptionParser();
        var pathConverter = new PathValueConverter();

        var srcDirArg = parser.accepts("src")
                              .withRequiredArg()
                              .describedAs("The source jar")
                              .required()
                              .withValuesConvertedBy(pathConverter);
        var outDirArg = parser.accepts("out")
                              .withRequiredArg()
                              .describedAs("The output jar")
                              .required()
                              .withValuesConvertedBy(pathConverter);
        var rsaKeyUrl = parser.accepts("rsa-key")
                              .withRequiredArg()
                              .describedAs("The URL to download the rsa key from");
        var options = parser.parse(args);
        return new PatcherOptions(
                options.valueOf(srcDirArg),
                options.valueOf(outDirArg),
                options.valueOf(rsaKeyUrl)
        );
    }

    private static class PathValueConverter implements ValueConverter<Path> {

        @Override
        public Path convert(String value) {
            return Path.of(value);
        }

        @Override
        public Class<? extends Path> valueType() {
            return Path.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
