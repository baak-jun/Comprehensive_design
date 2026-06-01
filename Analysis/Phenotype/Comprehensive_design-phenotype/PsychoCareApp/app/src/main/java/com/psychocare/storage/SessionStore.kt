package com.psychocare.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.psychocare.data.PhenotypeData
import com.psychocare.data.Photo
import com.psychocare.data.UserProfile
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 분석 결과 영속화 저장소
 *
 * 사진 분석 프로필 · 피노타입 · 분석된 사진 목록을 내부 저장소에 JSON으로 저장하고
 * 앱 재실행 시 복원한다. (앱을 나갔다 와도 분석 결과 유지)
 */
object SessionStore {

    private const val TAG = "SessionStore"
    private const val PROFILE_FILE   = "saved_profile.json"
    private const val PHENO_FILE     = "saved_phenotype.json"
    private const val PHOTOS_FILE    = "saved_photos.json"

    private val gson = Gson()

    // ── 저장 ────────────────────────────────────────────────────────

    fun saveProfile(context: Context, profile: UserProfile?) = write(context, PROFILE_FILE, profile)

    fun savePhenotype(context: Context, data: PhenotypeData?) = write(context, PHENO_FILE, data)

    fun savePhotos(context: Context, photos: List<Photo>) = write(context, PHOTOS_FILE, photos)

    // ── 불러오기 ────────────────────────────────────────────────────

    fun loadProfile(context: Context): UserProfile? =
        read(context, PROFILE_FILE, UserProfile::class.java)

    fun loadPhenotype(context: Context): PhenotypeData? =
        read(context, PHENO_FILE, PhenotypeData::class.java)

    fun loadPhotos(context: Context): List<Photo> {
        val file = File(context.filesDir, PHOTOS_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Photo>>() {}.type
            gson.fromJson<List<Photo>>(file.readText(), type) ?: emptyList()
        }.getOrElse {
            Log.e(TAG, "사진 목록 로드 실패: ${it.message}")
            emptyList()
        }
    }

    fun hasSavedSession(context: Context): Boolean =
        File(context.filesDir, PROFILE_FILE).exists()

    fun clear(context: Context) {
        listOf(PROFILE_FILE, PHENO_FILE, PHOTOS_FILE).forEach {
            File(context.filesDir, it).delete()
        }
        Log.d(TAG, "저장된 세션 삭제")
    }

    // ── 내부 유틸 ───────────────────────────────────────────────────

    private fun write(context: Context, name: String, obj: Any?) {
        runCatching {
            val file = File(context.filesDir, name)
            if (obj == null) { file.delete(); return }
            file.writeText(gson.toJson(obj))
            Log.d(TAG, "$name 저장 완료")
        }.onFailure { Log.e(TAG, "$name 저장 실패: ${it.message}") }
    }

    private fun <T> read(context: Context, name: String, clazz: Class<T>): T? {
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(), clazz)
        }.getOrElse {
            Log.e(TAG, "$name 로드 실패: ${it.message}")
            null
        }
    }
}
