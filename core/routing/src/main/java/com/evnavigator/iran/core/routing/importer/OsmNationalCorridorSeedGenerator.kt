package com.evnavigator.iran.core.routing.importer

/**
 * V12.5 Extracted Collaborator: Synthetic Iran National Corridor Seed Dataset Generator
 * used by unit tests and offline bootstrap verification.
 */
internal object OsmNationalCorridorSeedGenerator {

    fun generateDataset(importer: OsmPbfGraphImporter): ImportedCorridorDataset {
        val nodes = linkedMapOf(
            1001L to OsmNodeRecord(1001L, 35.6997, 51.3380, 1190.0, "میدان آزادی تهران"),
            1002L to OsmNodeRecord(1002L, 35.5350, 51.3500, 1050.0, "عوارضی تهران-قم"),
            1003L to OsmNodeRecord(1003L, 34.6401, 50.8764, 930.0, "مجتمع مهروماه قم"),
            1004L to OsmNodeRecord(1004L, 33.9850, 51.4100, 980.0, "تقاطع کاشان"),
            1005L to OsmNodeRecord(1005L, 33.5120, 51.8900, 1620.0, "نطنز"),
            1006L to OsmNodeRecord(1006L, 32.6370, 51.6830, 1575.0, "پل خواجو اصفهان"),
            1007L to OsmNodeRecord(1007L, 32.0120, 51.8750, 1820.0, "شهرضا"),
            1008L to OsmNodeRecord(1008L, 31.1820, 52.6520, 2010.0, "آباده"),
            1009L to OsmNodeRecord(1009L, 29.6360, 52.5590, 1540.0, "دروازه قرآن شیراز"),
            1010L to OsmNodeRecord(1010L, 35.8450, 50.9320, 1310.0, "کرج"),
            1011L to OsmNodeRecord(1011L, 36.2790, 50.0430, 1280.0, "قزوین"),
            1012L to OsmNodeRecord(1012L, 36.6760, 48.4960, 1630.0, "زنجان"),
            1013L to OsmNodeRecord(1013L, 38.0260, 46.3680, 1450.0, "ائل‌گلی تبریز"),
            1014L to OsmNodeRecord(1014L, 37.2800, 49.5830, 15.0, "میدان شهرداری رشت"),
            1015L to OsmNodeRecord(1015L, 35.2250, 52.3420, 860.0, "گرمسار"),
            1016L to OsmNodeRecord(1016L, 35.5820, 53.4020, 1130.0, "سمنان"),
            1017L to OsmNodeRecord(1017L, 36.4180, 54.9750, 1345.0, "شاهرود"),
            1018L to OsmNodeRecord(1018L, 36.2180, 57.6750, 975.0, "سبزوار"),
            1019L to OsmNodeRecord(1019L, 36.2870, 59.6150, 985.0, "مشهد مقدس"),
            1020L to OsmNodeRecord(1020L, 31.8940, 54.3710, 1216.0, "یزد"),
            1021L to OsmNodeRecord(1021L, 30.2910, 57.0670, 1755.0, "کرمان"),
            1022L to OsmNodeRecord(1022L, 27.1830, 56.2660, 10.0, "بندرعباس")
        )

        val ways = listOf(
            OsmWayRecord(
                id = 5001L,
                nodeIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L, 1006L),
                tags = mapOf(
                    "highway" to "motorway",
                    "name:fa" to "آزادراه تهران-قم-اصفهان (آزادراه خلیج فارس)",
                    "maxspeed" to "120",
                    "oneway" to "no",
                    "toll" to "yes",
                    "surface" to "asphalt"
                )
            ),
            OsmWayRecord(
                id = 5002L,
                nodeIds = listOf(1006L, 1007L, 1008L, 1009L),
                tags = mapOf(
                    "highway" to "trunk",
                    "name:fa" to "بزرگراه اصفهان-شیراز",
                    "maxspeed" to "110",
                    "oneway" to "no",
                    "surface" to "asphalt"
                )
            ),
            OsmWayRecord(
                id = 5003L,
                nodeIds = listOf(1001L, 1010L, 1011L, 1012L, 1013L),
                tags = mapOf(
                    "highway" to "motorway",
                    "name:fa" to "آزادراه تهران-قزوین-زنجان-تبریز",
                    "maxspeed" to "120",
                    "oneway" to "no",
                    "toll" to "yes",
                    "surface" to "asphalt"
                )
            ),
            OsmWayRecord(
                id = 5004L,
                nodeIds = listOf(1011L, 1014L),
                tags = mapOf(
                    "highway" to "motorway",
                    "name:fa" to "آزادراه قزوین-رشت",
                    "maxspeed" to "110",
                    "oneway" to "no",
                    "surface" to "asphalt"
                )
            ),
            OsmWayRecord(
                id = 5005L,
                nodeIds = listOf(1001L, 1015L, 1016L, 1017L, 1018L, 1019L),
                tags = mapOf(
                    "highway" to "trunk",
                    "name:fa" to "بزرگراه امام رضا (تهران-سمنان-مشهد)",
                    "maxspeed" to "115",
                    "oneway" to "no",
                    "surface" to "asphalt"
                )
            ),
            OsmWayRecord(
                id = 5006L,
                nodeIds = listOf(1006L, 1020L, 1021L, 1022L),
                tags = mapOf(
                    "highway" to "trunk",
                    "name:fa" to "کریدور ترانزیت اصفهان-یزد-کرمان-بندرعباس",
                    "maxspeed" to "110",
                    "oneway" to "no",
                    "surface" to "asphalt"
                )
            )
        )

        val relations = listOf(
            OsmRelationRecord(
                id = 9001L,
                members = listOf(
                    OsmRelationMember("way", 5001L, "from"),
                    OsmRelationMember("node", 1006L, "via"),
                    OsmRelationMember("way", 5006L, "to")
                ),
                tags = mapOf("type" to "restriction", "restriction" to "no_u_turn")
            )
        )

        return importer.buildDatasetFromRecords(nodes, ways, relations)
    }
}
