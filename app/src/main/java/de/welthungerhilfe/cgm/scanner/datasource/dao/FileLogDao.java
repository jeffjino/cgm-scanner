/*
 * Child Growth Monitor - quick and accurate data on malnutrition
 * Copyright (c) 2018 Markus Matiaschek <mmatiaschek@gmail.com>
 * Copyright (c) 2018 Welthungerhilfe Innovation
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.welthungerhilfe.cgm.scanner.datasource.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import de.welthungerhilfe.cgm.scanner.datasource.models.FileLog;
import de.welthungerhilfe.cgm.scanner.datasource.models.UploadStatus;

import static androidx.room.OnConflictStrategy.REPLACE;
import static de.welthungerhilfe.cgm.scanner.datasource.database.CgmDatabase.TABLE_FILE_LOG;

@Dao
public interface FileLogDao {
    @Insert(onConflict = REPLACE)
    void saveFileLog(FileLog log);

    @Update(onConflict = REPLACE)
    void updateFileLog(FileLog log);

    @Query("SELECT * FROM " + TABLE_FILE_LOG + " WHERE deleted=0 AND environment=:environment LIMIT 15")
    List<FileLog> loadQueuedData(int environment);

    @Query("SELECT * FROM " + TABLE_FILE_LOG + " WHERE deleted=1 AND environment=:environment AND status!=203 AND type LIKE 'consent'")
    List<FileLog> loadConsentFile(int environment);

    @Query("SELECT COUNT(id) FROM " + TABLE_FILE_LOG + " WHERE deleted=0")
    long getArtifactCount();

    @Query("SELECT SUM(fileSize)/1024/1024 FROM " + TABLE_FILE_LOG + " WHERE deleted=0")
    double getArtifactFileSize();

    @Query("SELECT COUNT(id) FROM " + TABLE_FILE_LOG + " WHERE deleted=1")
    long getDeletedArtifactCount();

    @Query("SELECT COUNT(id) FROM " + TABLE_FILE_LOG)
    long getTotalArtifactCount();

    @Query("SELECT SUM(fileSize)/1024/1024 FROM " + TABLE_FILE_LOG)
    double getTotalArtifactFileSize();

    @Query("SELECT * FROM " + TABLE_FILE_LOG)
    List<FileLog> getAll();

    @Query("SELECT * FROM " + TABLE_FILE_LOG + " WHERE measureId=:measureId And environment=:environment ORDER BY createDate")
    List<FileLog> getArtifactsForMeasure(String measureId,int environment);

    @Query("SELECT uploaded, total  FROM (SELECT SUM(fileSize) AS total FROM file_logs WHERE measureId LIKE :measureId), (SELECT SUM(fileSize) AS uploaded FROM file_logs WHERE measureId LIKE :measureId AND deleted=1)")
    LiveData<UploadStatus> getMeasureUploadProgress(String measureId);
}
