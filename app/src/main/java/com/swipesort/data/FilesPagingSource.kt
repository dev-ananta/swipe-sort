package com.swipesort.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.swipesort.domain.FileItem
import com.swipesort.domain.FileQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Pages through MediaStore with LIMIT/OFFSET so even libraries with 100k+ files load instantly and
 * scroll smoothly: only the pages near the screen are ever in memory.
 */
class FilesPagingSource(
    private val repo: FileRepository,
    private val query: FileQuery,
) : PagingSource<Int, FileItem>() {

    companion object { const val PAGE = 60 }

    // If files appear/disappear while scrolling, offsets can shift and a file could arrive twice.
    // Lists are keyed by id, so duplicates are dropped here (each new source starts fresh).
    private val seen = ConcurrentHashMap.newKeySet<Long>()

    override fun getRefreshKey(state: PagingState<Int, FileItem>): Int? =
        state.anchorPosition?.let { pos -> (pos / PAGE) * PAGE }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileItem> {
        val offset = params.key ?: 0
        return try {
            val rows = withContext(Dispatchers.IO) { repo.queryPage(query, offset, PAGE) }
            LoadResult.Page(
                data = rows.filter { seen.add(it.id) },
                prevKey = if (offset == 0) null else maxOf(0, offset - PAGE),
                nextKey = if (rows.size < PAGE) null else offset + PAGE,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
