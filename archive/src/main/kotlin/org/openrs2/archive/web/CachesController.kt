package org.openrs2.archive.web

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondOutputStream
import io.ktor.thymeleaf.ThymeleafContent
import io.netty.buffer.ByteBufAllocator
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.openrs2.archive.cache.CacheExporter
import org.openrs2.archive.map.MapRenderer
import org.openrs2.cache.DiskStoreZipWriter
import org.openrs2.cache.FlatFileStoreTarWriter
import org.openrs2.compress.gzip.GzipLevelOutputStream
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class CachesController @Inject constructor(
    private val exporter: CacheExporter,
    private val renderer: MapRenderer,
    private val alloc: ByteBufAllocator
) {
    private val renderSemaphore = Semaphore(1)

    public suspend fun index(call: ApplicationCall) {
        val caches = exporter.list()
        call.respond(ThymeleafContent("caches/index.html", mapOf("caches" to caches)))
    }

    public suspend fun indexJson(call: ApplicationCall) {
        val caches = exporter.list()
        call.respond(caches)
    }

    public suspend fun show(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val cache = exporter.get(id)
        if (cache == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.respond(ThymeleafContent("caches/show.html", mapOf("cache" to cache)))
    }

    public suspend fun exportDisk(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val name = exporter.getFileName(id)
        if (name == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "cache-$name.zip")
                .toString()
        )

        call.respondOutputStream(contentType = ContentType.Application.Zip) {
            exporter.export(id) { legacy ->
                DiskStoreZipWriter(ZipOutputStream(this), alloc = alloc, legacy = legacy)
            }
        }
    }

    public suspend fun exportFlatFile(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val name = exporter.getFileName(id)
        if (name == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "cache-$name.tar.gz")
                .toString()
        )

        call.respondOutputStream(contentType = ContentType.Application.GZip) {
            exporter.export(id) {
                FlatFileStoreTarWriter(TarArchiveOutputStream(GzipLevelOutputStream(this, Deflater.BEST_COMPRESSION)))
            }
        }
    }

    public suspend fun exportKeysJson(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val name = exporter.getFileName(id)
        if (name == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Inline
                .withParameter(ContentDisposition.Parameters.FileName, "keys-$name.json")
                .toString()
        )

        call.respond(exporter.exportKeys(id))
    }

    public suspend fun exportKeysZip(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val name = exporter.getFileName(id)
        if (name == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "keys-$name.zip")
                .toString()
        )

        call.respondOutputStream(contentType = ContentType.Application.Zip) {
            ZipOutputStream(this).use { output ->
                output.bufferedWriter().use { writer ->
                    output.setLevel(Deflater.BEST_COMPRESSION)

                    val timestamp = FileTime.from(Instant.EPOCH)

                    for (key in exporter.exportKeys(id)) {
                        if (key.mapSquare == null) {
                            continue
                        }

                        val entry = ZipEntry("keys/${key.mapSquare}.txt")
                        entry.creationTime = timestamp
                        entry.lastAccessTime = timestamp
                        entry.lastModifiedTime = timestamp

                        output.putNextEntry(entry)

                        writer.write(key.key.k0.toString())
                        writer.write('\n'.code)

                        writer.write(key.key.k1.toString())
                        writer.write('\n'.code)

                        writer.write(key.key.k2.toString())
                        writer.write('\n'.code)

                        writer.write(key.key.k3.toString())
                        writer.write('\n'.code)

                        writer.flush()
                    }
                }
            }
        }
    }

    public suspend fun renderMap(call: ApplicationCall) {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val name = exporter.getFileName(id)
        if (name == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Inline
                .withParameter(ContentDisposition.Parameters.FileName, "map-$name.png")
                .toString()
        )

        /*
         * The temporary BufferedImages used by the MapRenderer use a large
         * amount of heap space. We limit the number of renders that can be
         * performed in parallel to prevent OOMs.
         */
        renderSemaphore.withPermit {
            val image = renderer.render(id)

            call.respondOutputStream(contentType = ContentType.Image.PNG) {
                ImageIO.write(image, "PNG", this)
            }
        }
    }
}
