package com.theplug.AutoTitheFarmPlugin;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import lombok.Getter;
import net.runelite.api.ObjectID;

public enum TitheFarmPlantType {
    EMPTY("Empty", ObjectID.TITHE_PATCH,
            ObjectID.TITHE_PATCH
    ),
    GOLOVANOVA("Golovanova", ObjectID.GOLOVANOVA_PLANT_27393,
            ObjectID.GOLOVANOVA_SEEDLING, ObjectID.GOLOVANOVA_SEEDLING_27385, ObjectID.BLIGHTED_GOLOVANOVA_SEEDLING,
            ObjectID.GOLOVANOVA_PLANT, ObjectID.GOLOVANOVA_PLANT_27388, ObjectID.BLIGHTED_GOLOVANOVA_PLANT,
            ObjectID.GOLOVANOVA_PLANT_27390, ObjectID.GOLOVANOVA_PLANT_27391, ObjectID.BLIGHTED_GOLOVANOVA_PLANT_27392,
            ObjectID.GOLOVANOVA_PLANT_27393, ObjectID.BLIGHTED_GOLOVANOVA_PLANT_27394
    ),
    BOLOGANO("Bologano", ObjectID.BOLOGANO_PLANT_27404,
            ObjectID.BOLOGANO_SEEDLING, ObjectID.BOLOGANO_SEEDLING_27396, ObjectID.BLIGHTED_BOLOGANO_SEEDLING,
            ObjectID.BOLOGANO_PLANT, ObjectID.BOLOGANO_PLANT_27399, ObjectID.BLIGHTED_BOLOGANO_PLANT,
            ObjectID.BOLOGANO_PLANT_27401, ObjectID.BOLOGANO_PLANT_27402, ObjectID.BLIGHTED_BOLOGANO_PLANT_27403,
            ObjectID.BOLOGANO_PLANT_27404, ObjectID.BLIGHTED_BOLOGANO_PLANT_27405
    ),
    LOGAVANO("Logavano", ObjectID.LOGAVANO_PLANT_27415,
            ObjectID.LOGAVANO_SEEDLING, ObjectID.LOGAVANO_SEEDLING_27407, ObjectID.BLIGHTED_LOGAVANO_SEEDLING,
            ObjectID.LOGAVANO_PLANT, ObjectID.LOGAVANO_PLANT_27410, ObjectID.BLIGHTED_LOGAVANO_PLANT,
            ObjectID.LOGAVANO_PLANT_27412, ObjectID.LOGAVANO_PLANT_27413, ObjectID.BLIGHTED_LOGAVANO_PLANT_27414,
            ObjectID.LOGAVANO_PLANT_27415, ObjectID.BLIGHTED_LOGAVANO_PLANT_27416
    );

    @Getter
    private final String name;
    @Getter
    private final int baseId;
    @Getter
    private final int[] objectIds;

    private static final Map<Integer, TitheFarmPlantType> plantTypes;

    static {
        ImmutableMap.Builder<Integer, TitheFarmPlantType> builder = new ImmutableMap.Builder<>();

        for (TitheFarmPlantType type : values()) {
            for (int spotId : type.getObjectIds()) {
                builder.put(spotId, type);
            }
        }

        plantTypes = builder.build();
    }

    TitheFarmPlantType(String name, int baseId, int... objectIds) {
        this.name = name;
        this.baseId = baseId;
        this.objectIds = objectIds;
    }

    public static TitheFarmPlantType getPlantType(int objectId) {
        return plantTypes.get(objectId);
    }
}
