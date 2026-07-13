package com.pulse.replay;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class S3RawArchiveClientTest {

    @Test
    void replayDate가Null이면실패한다() {
        S3RawArchiveClient client = clientWithDate(null);

        assertThatIllegalStateException()
                .isThrownBy(client::validateDate)
                .withMessage("pulse.replay.date is required for the replay profile");
    }

    @Test
    void replayDate가공백이면실패한다() {
        S3RawArchiveClient client = clientWithDate("   ");

        assertThatIllegalStateException()
                .isThrownBy(client::validateDate)
                .withMessage("pulse.replay.date is required for the replay profile");
    }

    private static S3RawArchiveClient clientWithDate(String date) {
        ReplayProperties properties = new ReplayProperties(null, null, date, 200);
        return new S3RawArchiveClient(properties, new ObjectMapper());
    }
}
