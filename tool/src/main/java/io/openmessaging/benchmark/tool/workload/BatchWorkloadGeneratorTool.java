/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.tool.workload;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchWorkloadGeneratorTool {

    public static void main(String[] args) throws IOException {
        final BatchWorkloadGeneratorTool.Arguments arguments =
                new BatchWorkloadGeneratorTool.Arguments();
        JCommander jc = new JCommander(arguments);
        jc.setProgramName("batch-workload-generator");

        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            jc.usage();
            System.exit(-1);
        }

        if (arguments.help) {
            jc.usage();
            System.exit(-1);
        }

        // Dump configuration variables
        Path templateFolder = arguments.templateFolder;
        Path outputFolderRoot = arguments.outputFolder;
        log.info(
                "Starting batch benchmark generator with config: templateFolder={} outputFolder={}",
                templateFolder,
                outputFolderRoot);

        try (Stream<Path> fileStream = Files.walk(templateFolder)) {
            fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .forEach(
                            templateFile -> {
                                try {
                                    Path inputFolder = templateFile.getParent();
                                    Path relativeFolderPath = templateFolder.relativize(inputFolder);
                                    Path outputFolder = outputFolderRoot.resolve(relativeFolderPath);
                                    Files.createDirectories(outputFolder);
                                    log.info(
                                            "Generating workload from template {} to {}", templateFile, outputFolder);
                                    WorkloadGenerationTool.generateWorkloadsFromTemplate(
                                            templateFile.toFile(), outputFolder.toFile());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }
    }

    static class Arguments {
        @Parameter(
                names = {"-t", "--template-folder"},
                description = "Path to a YAML file containing the workload template",
                required = true)
        public Path templateFolder;

        @Parameter(
                names = {"-o", "--output-folder"},
                description = "Output",
                required = true)
        public Path outputFolder;

        @Parameter(
                names = {"-h", "--help"},
                description = "Help message",
                help = true)
        boolean help;
    }
}
