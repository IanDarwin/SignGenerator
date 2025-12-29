package text3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignConvertTest {

    final static String JSON = """
            {
                "text": "Must be a\\nSign!!",
                "fontName": "Times Roman",
                "fontSize": 40,
                "fontStyle": 1,
                "alignment": "CENTER",
                "baseHeight": 1.000000,
                "baseMargin": 2.000000,
                "letterHeight": 3.000000,
                "bevelHeight": 4.000000
            }""";

    final Sign SIGN = new Sign(
                "Must be a\nSign!!",
                "Times Roman", 40, 1,
                TextAlign.CENTER,
                1,2,3,4
        );

	@Test
	public void testToJson() {
        var actual = SIGN.toJSON();
        assertEquals(JSON, actual);
	}

	@Test
	public void testFromJson() {
        var actual = Sign.fromJSON(JSON);
        assertEquals(SIGN, actual);
	}
}
