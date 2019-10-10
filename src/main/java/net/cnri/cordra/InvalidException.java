package net.cnri.cordra;

import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class InvalidException extends Exception {
    ProcessingReport report;

    public InvalidException() {
    }

    public InvalidException(Throwable cause) {
        super(cause);
    }

    public InvalidException(ProcessingReport report) {
        super(messageFromReport(report));
        this.report = report;
    }

    public InvalidException(ProcessingReport report, Throwable cause) {
        super(messageFromReport(report), cause);
        this.report = report;
    }

    public InvalidException(String message) {
        super(message);
    }

    public InvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessingReport getReport() {
        return report;
    }

    private static String messageFromReport(ProcessingReport report) {
        for (ProcessingMessage msg : report) {
            if (msg.getLogLevel() == LogLevel.ERROR || msg.getLogLevel() == LogLevel.FATAL) {
                String jsonPointer = msg.asJson().at("/instance/pointer").textValue();
                if (jsonPointer != null) {
                    return jsonPointer + ": " + msg.getMessage();
                } else {
                    return msg.getMessage();
                }
            }
        }
        return "Unexpected processing report";
    }
}
