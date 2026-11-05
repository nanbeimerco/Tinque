package com.music.bitchord.feature.tageditor.domain.model

import androidx.annotation.StringRes
import com.music.bitchord.R

/**
 * One editable tag, so a save can write some of them and leave the rest of a
 * file's tags exactly as they are — what applying an album's details to every
 * song in its folder needs.
 *
 * @property propertyKey the TagLib property it is stored under; null for the artwork.
 * @property perSong whether songs normally differ in it (title, track, lyrics):
 * those are not ticked for a folder by default.
 */
enum class TagField(val propertyKey: String?, @StringRes val label: Int, val perSong: Boolean = false) {
    TITLE("TITLE", R.string.l10n_title, perSong = true),
    ALBUM("ALBUM", R.string.l10n_album),
    ARTIST("ARTIST", R.string.l10n_artist),
    ALBUM_ARTIST("ALBUMARTIST", R.string.l10n_album_artist),
    COMPOSER("COMPOSER", R.string.l10n_composer),
    PRODUCER("PRODUCER", R.string.l10n_producer),
    PUBLISHER("COPYRIGHT", R.string.l10n_publisher),
    GENRE("GENRE", R.string.l10n_genre),
    YEAR("DATE", R.string.l10n_year),
    TRACK("TRACKNUMBER", R.string.l10n_track, perSong = true),
    TRACK_TOTAL("TRACKTOTAL", R.string.l10n_track_total),
    DISC("DISCNUMBER", R.string.l10n_disc),
    DISC_TOTAL("DISCTOTAL", R.string.l10n_disc_total),
    LYRICS("LYRICS", R.string.l10n_lyrics, perSong = true),
    LYRICIST("LYRICIST", R.string.l10n_lyricist),
    ARRANGER("ARRANGER", R.string.l10n_arranger),
    COMMENT("COMMENT", R.string.l10n_comment),
    ARTWORK(null, R.string.l10n_cover_art);

    fun get(data: SongTagData): String = when (this) {
        TITLE -> data.title
        ALBUM -> data.album
        ARTIST -> data.artist
        ALBUM_ARTIST -> data.albumArtist
        COMPOSER -> data.composer
        PRODUCER -> data.conductor
        PUBLISHER -> data.publisher
        GENRE -> data.genre
        YEAR -> data.year
        TRACK -> data.trackNumber
        TRACK_TOTAL -> data.trackTotal
        DISC -> data.discNumber
        DISC_TOTAL -> data.discTotal
        LYRICS -> data.lyrics
        LYRICIST -> data.lyricist
        ARRANGER -> data.arranger
        COMMENT -> data.comment
        ARTWORK -> ""
    }

    fun set(data: SongTagData, value: String): SongTagData = when (this) {
        TITLE -> data.copy(title = value)
        ALBUM -> data.copy(album = value)
        ARTIST -> data.copy(artist = value)
        ALBUM_ARTIST -> data.copy(albumArtist = value)
        COMPOSER -> data.copy(composer = value)
        PRODUCER -> data.copy(conductor = value)
        PUBLISHER -> data.copy(publisher = value)
        GENRE -> data.copy(genre = value)
        YEAR -> data.copy(year = value)
        TRACK -> data.copy(trackNumber = value)
        TRACK_TOTAL -> data.copy(trackTotal = value)
        DISC -> data.copy(discNumber = value)
        DISC_TOTAL -> data.copy(discTotal = value)
        LYRICS -> data.copy(lyrics = value)
        LYRICIST -> data.copy(lyricist = value)
        ARRANGER -> data.copy(arranger = value)
        COMMENT -> data.copy(comment = value)
        ARTWORK -> data
    }

    companion object {
        /** The fields [edited] differs from [original] in. */
        fun changed(original: SongTagData, edited: SongTagData): Set<TagField> =
            entries.filterTo(LinkedHashSet()) { field ->
                if (field == ARTWORK) edited.artworkChanged else field.get(edited).trim() != field.get(original).trim()
            }
    }
}
