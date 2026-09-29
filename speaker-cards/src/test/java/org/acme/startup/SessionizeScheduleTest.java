package org.acme.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionizeScheduleTest {
    @TempDir
    Path directory;

    @Test
    void countsCoSpeakersAndReadsExcelDateCells() throws Exception {
        Path schedule = directory.resolve("accepted.xlsx");
        writeSchedule(schedule, true);

        SessionizeSchedule.Summary summary = new SessionizeSchedule().inspect(schedule);
        assertEquals(2, summary.rows());
        assertEquals(2, summary.speakers());
        assertEquals(1, summary.talks());
        assertEquals(5, summary.cards());
        assertEquals(Set.of(2027), summary.years());
    }

    @Test
    void rejectsChangedSessionizeColumnOrder() throws Exception {
        Path schedule = directory.resolve("changed.xlsx");
        writeSchedule(schedule, false);
        assertEquals("Column 1 must be Session Id",
                assertThrows(IllegalArgumentException.class, () -> new SessionizeSchedule().inspect(schedule)).getMessage());
    }

    private void writeSchedule(Path path, boolean correctHeader) throws Exception {
        String[] headers = {"Session Id", "Title", "Description", "Owner", "Owner Email",
                "Owner Informed", "Owner Confirmed", "Room", "Scheduled At", "Scheduled Duration",
                "Live Link", "Recording Link", "Speaker Id", "FirstName", "LastName", "Email",
                "TagLine", "Bio", "LinkedIn", "Facebook", "Blog", "Instagram", "Profile Picture"};
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Accepted sessions and speakers");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(i == 0 && !correctHeader ? "Talk Id" : headers[i]);
            }
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("m/d/yy h:mm"));
            for (int i = 1; i <= 2; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue(12345);
                if (i == 1) {
                    row.createCell(1).setCellValue("A talk with two speakers");
                }
                var date = row.createCell(8);
                date.setCellValue(LocalDateTime.of(2027, 1, 22, 10, 0));
                date.setCellStyle(dateStyle);
                row.createCell(9).setCellValue(60);
                row.createCell(12).setCellValue(UUID.randomUUID().toString());
                row.createCell(13).setCellValue("Test");
                row.createCell(14).setCellValue("Speaker " + i);
                row.createCell(17).setCellValue("A biography");
            }
            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }
}
