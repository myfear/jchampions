package org.acme.startup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SessionizeSchedule {

    private static final String[] HEADERS = {
            "Session Id", "Title", "Description", "Owner", "Owner Email", "Owner Informed",
            "Owner Confirmed", "Room", "Scheduled At", "Scheduled Duration", "Live Link",
            "Recording Link", "Speaker Id", "FirstName", "LastName", "Email", "TagLine",
            "Bio", "LinkedIn", "Facebook", "Blog", "Instagram", "Profile Picture"
    };

    public record Summary(int rows, int speakers, int talks, Set<Integer> years) {
        public int cards() {
            return speakers * 2 + talks;
        }
    }

    public Summary inspect(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Schedule not found: " + path);
        }
        try (InputStream input = Files.newInputStream(path); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("The workbook has no header row");
            }
            for (int i = 0; i < HEADERS.length; i++) {
                if (!HEADERS[i].equals(value(header.getCell(i)))) {
                    throw new IllegalArgumentException("Column " + (i + 1) + " must be " + HEADERS[i]);
                }
            }

            Set<Long> talks = new HashSet<>();
            Set<UUID> speakers = new HashSet<>();
            Set<String> pairs = new HashSet<>();
            Set<Integer> years = new HashSet<>();
            int rows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String[] fields = fields(row);
                try {
                    long talkId = Long.parseLong(fields[0].trim());
                    UUID speakerId = UUID.fromString(fields[12].trim());
                    if (talkId <= 0 || (!talks.contains(talkId) && fields[1].isBlank())) {
                        throw new IllegalArgumentException("session ID or first title is missing");
                    }
                    if (fields[13].isBlank() || fields[14].isBlank() || fields[17].isBlank()) {
                        throw new IllegalArgumentException("speaker name or biography is missing");
                    }
                    if (Integer.parseInt(fields[9].trim()) <= 0) {
                        throw new IllegalArgumentException("duration must be positive");
                    }
                    int year = LocalDateTime.parse(fields[8].trim()).getYear();
                    if (!pairs.add(talkId + ":" + speakerId)) {
                        throw new IllegalArgumentException("duplicate speaker on session " + talkId);
                    }
                    talks.add(talkId);
                    speakers.add(speakerId);
                    years.add(year);
                    rows++;
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Row " + (rowIndex + 1) + ": " + e.getMessage(), e);
                }
            }
            if (rows == 0) {
                throw new IllegalArgumentException("The workbook has no session rows");
            }
            return new Summary(rows, speakers.size(), talks.size(), Set.copyOf(years));
        }
    }

    public static String[] fields(Row row) {
        String[] fields = new String[HEADERS.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = value(row.getCell(i));
        }
        return fields;
    }

    private static String value(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : Long.toString((long) cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
