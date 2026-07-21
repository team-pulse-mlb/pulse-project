package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import java.util.List;

record TerminalDrainData(List<BdlPlay> plays, List<BdlPlateAppearance> plateAppearances) {
}
