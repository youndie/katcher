package io.github.youndie.katcher.feature.symbolication.data

import io.github.youndie.katcher.feature.app.data.Apps
import io.github.youndie.katcher.feature.symbolication.MappingType
import io.github.youndie.katcher.feature.symbolication.data.SymbolMaps.TABLE_NAME
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object SymbolMaps : LongIdTable(TABLE_NAME) {
    val appId = reference(COLUMN_APP_ID, Apps)
    val buildUuid = varchar(COLUMN_BUILD_UUID, 128)
    val mapType = enumerationByName(COLUMN_MAP_TYPE, 16, MappingType::class)
    val filePath = varchar(COLUMN_FILE_PATH, 255)
    val versionName = varchar(COLUMN_VERSION_NAME, 64).nullable()
    val createdAt = long(COLUMN_CREATED_AT)

    init {
        index("idx_symbol_maps_lookup", isUnique = false, appId, buildUuid)
    }

    const val TABLE_NAME = "symbol_maps"
    const val COLUMN_APP_ID = "app_id"
    const val COLUMN_BUILD_UUID = "build_uuid"
    const val COLUMN_MAP_TYPE = "map_type"
    const val COLUMN_FILE_PATH = "file_path"
    const val COLUMN_VERSION_NAME = "version_name"
    const val COLUMN_CREATED_AT = "created_at"
}
