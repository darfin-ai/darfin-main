package com.kosta.darfin.service.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.disclosure.DartDocumentResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DartDocumentServiceBlocksTest {

    @Test
    void parseBlocksMatchesPythonSampleResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getClassLoader().getResourceAsStream("doc_resp2.json");
        JsonNode root = mapper.readTree(is);
        String text = root.path("text").asText();

        DartDocumentService service = new DartDocumentService(new RestTemplate(), mapper);
        Method parseBlocks = DartDocumentService.class.getDeclaredMethod("parseBlocks", JsonNode.class);
        parseBlocks.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<DartDocumentResponseDto.Block> blocks =
                (List<DartDocumentResponseDto.Block>) parseBlocks.invoke(service, root.path("blocks"));

        assertEquals(root.path("blocks").size(), blocks.size());

        int tableCount = 0;
        for (DartDocumentResponseDto.Block b : blocks) {
            tableCount += countTables(b);
            if (b.getCharStart() != null) {
                String expected = plainText(b);
                String actual = text.substring(b.getCharStart(), b.getCharEnd());
                assertEquals(expected, actual, "block offset mismatch");
            }
        }
        assertTrue(tableCount > 0, "sample response should contain at least one table block");
        System.out.println("Parsed " + blocks.size() + " top-level blocks, " + tableCount + " tables (incl. nested). All offsets matched.");
    }

    /**
     * Python dart_collector.py의 block_plain_text와 동일한 규칙(셀 안 블록은 "\n", 셀은
     * " | ", 행은 "\n")으로 재귀 직렬화한다. 표 셀이 문자열이 아니라 Cell(rowSpan/colSpan +
     * 중첩 Block 목록)로 바뀌었기 때문에, 기대값도 같은 방식으로 재귀 계산해야 한다.
     */
    private static String plainText(DartDocumentResponseDto.Block b) {
        if ("table".equals(b.getType())) {
            return b.getRows().stream()
                    .map(row -> row.stream()
                            .map(cell -> cell.getBlocks().stream()
                                    .map(DartDocumentServiceBlocksTest::plainText)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.joining("\n")))
                            .collect(Collectors.joining(" | ")))
                    .collect(Collectors.joining("\n"));
        }
        return b.getText() == null ? "" : b.getText();
    }

    /** 표 블록 개수를 셀 안에 중첩된 표까지 재귀적으로 센다. */
    private static int countTables(DartDocumentResponseDto.Block b) {
        if (!"table".equals(b.getType())) {
            return 0;
        }
        int count = 1;
        for (List<DartDocumentResponseDto.Cell> row : b.getRows()) {
            for (DartDocumentResponseDto.Cell cell : row) {
                for (DartDocumentResponseDto.Block nested : cell.getBlocks()) {
                    count += countTables(nested);
                }
            }
        }
        return count;
    }
}
