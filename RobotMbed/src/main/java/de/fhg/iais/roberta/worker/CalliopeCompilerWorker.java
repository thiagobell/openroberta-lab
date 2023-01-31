package de.fhg.iais.roberta.worker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.bean.CompilerSetupBean;
import de.fhg.iais.roberta.components.Project;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.basic.Pair;


public class CalliopeCompilerWorker implements ICompilerWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CalliopeCompilerWorker.class);

    @Override
    public void execute(Project project) {
        String programName = project.getProgramName();
        String robot = project.getRobot();
        final CompilerSetupBean compilerWorkflowBean = project.getWorkerResult(CompilerSetupBean.class);


        final String compilerBinDir = compilerWorkflowBean.getCompilerBinDir();
        final String compilerResourcesDir = compilerWorkflowBean.getCompilerResourcesDir();
        final String tempDir = compilerWorkflowBean.getTempDir();
        final String crossCompilerSource = project.getSourceCode().toString();

        String token = project.getToken();
        String sourceCodeFileExtension = project.getSourceCodeFileExtension();

        CompileResponse buildResult = CalliopeCompilerWorker.runBuild(
                new CompileRequest(
                    crossCompilerSource,
                    robot,
                    token,
                    programName,
                    project.isNativeEditorCode()
                ),
                sourceCodeFileExtension,
                project.getBinaryFileExtension(),
                compilerBinDir,
                compilerResourcesDir,
                tempDir
        );
        // TODO: check how to do this sensibly, without having the UsedHardwareWorker beforehand
        project.setResult(buildResult.statusKey);
        project.addResultParam("MESSAGE", buildResult.compilerMessage);
        if ( buildResult.statusKey == Key.COMPILERWORKFLOW_SUCCESS ) {
            LOG.info("compile {} program {} successful", robot, programName);
        } else {
            LOG.error("compile of program {} for robot {} failed with message: {}: {}", programName, robot,
                    buildResult.statusKey, buildResult.compilerMessage);
        }
    }

    /**
     * create command to call the crosscompiler and execute the call.
     *
     * @return a pair of Key.COMPILERWORKFLOW_SUCCESS or Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED and the cross compiler output
     */

    private static CompileResponse runBuildRemotely(
            String endpoint,
            CompileRequest request) throws Exception {

        // make a rest call here

        throw new Exception("not implemented");

    }

    public static CompileResponse runBuild(
            CompileRequest request,
            String sourceCodeFileExtension,
            String binaryFileExtension,
            String compilerBinDir,
            String compilerResourcesDir,
            String tempDir
    ) {
        Util.storeGeneratedProgram(tempDir, request.crossCompilerSource, request.token, request.programName,
                "." + sourceCodeFileExtension);
        String scriptName = compilerResourcesDir + "../compile." + (SystemUtils.IS_OS_WINDOWS ? "bat" : "sh");
        boolean bluetooth = request.robot.equals("calliope2017");
        String bluetoothParam = bluetooth ? "-b" : "";
        Path pathToSrcFile = Paths.get(tempDir + request.token + "/" + request.programName);

        String[] executableWithParameters =
            {
                scriptName,
                compilerBinDir,
                request.programName,
                Paths.get("").resolve(pathToSrcFile).toAbsolutePath().normalize() + "/",
                compilerResourcesDir,
                bluetoothParam
            };

        Pair<Boolean, String> result = Util.runCrossCompiler(executableWithParameters, request.crossCompilerSource,
                request.isNativeEditorCode);
        Key resultKey = result.getFirst() ? Key.COMPILERWORKFLOW_SUCCESS : Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
        String compiledHex = null;
        if ( result.getFirst() ) {
            try {
                compiledHex = FileUtils
                            .readFileToString(
                                new File(pathToSrcFile + "/target/" + request.programName + "." + binaryFileExtension),
                                StandardCharsets.UTF_8);
                resultKey = Key.COMPILERWORKFLOW_SUCCESS;
            } catch ( IOException e ) {
                LOG.error("compilation of Calliope program successful, but reading the binary failed", e);
                resultKey = Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
            }
        }
        return new CompileResponse(resultKey, result.getSecond(), compiledHex);
    }
}
