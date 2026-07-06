package com.trs.modules.topology.dto;

import java.util.List;

public record TopologyDto(
        Long connectionId,
        String connectionName,
        String dbType,
        List<SchemaDto> schemas
) {}
