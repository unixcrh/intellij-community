package org.jetbrains.jps.gant;

/**
 * @author nik
 */
public class TeamCityBuildInfoPrinter implements BuildInfoPrinter {
  private static char escapedChar(char c) {
    switch (c) {
      case '\n': return 'n';
      case '\r': return 'r';
      case '\u0085': return 'x'; // next-line character
      case '\u2028': return 'l'; // line-separator character
      case '\u2029': return 'p'; // paragraph-separator character
      case '|': return '|';
      case '\'': return '\'';
      case '[': return '[';
      case ']': return ']';
    }

    return 0;
  }

  private static String escape(String text) {
    StringBuilder escaped = new StringBuilder();
    for (char c: text.toCharArray()) {
      Character escChar = escapedChar(c);
      if (escChar == 0) {
        escaped.append(c);
      } else {
        escaped.append('|').append(escChar);
      }
    }

    return escaped.toString();
  }

  @Override
  public void printProgressMessage(JpsGantProjectBuilder project, String message) {
    String escapedMessage = escape(message);
    project.info("##teamcity[progressMessage '" + escapedMessage + "']");
  }

  @Override
  public void printCompilationErrors(JpsGantProjectBuilder project, String compilerName, String messages) {
    String escapedCompiler = escape(compilerName);
    String escapedOutput = escape(messages);
    project.info("##teamcity[compilationStarted compiler='" + escapedCompiler + "']");
    project.info("##teamcity[message text='" + escapedOutput + "' status='ERROR']");
    project.info("##teamcity[compilationFinished compiler='" + escapedCompiler + "']");
  }

  @Override
  public void printCompilationStart(JpsGantProjectBuilder project, String compilerName) {
    project.info("##teamcity[compilationStarted compiler='" + escape(compilerName) + "']");
  }

  @Override
  public void printCompilationFinish(JpsGantProjectBuilder project, String compilerName) {
    project.info("##teamcity[compilationFinished compiler='" + escape(compilerName) + "']");
  }
}
