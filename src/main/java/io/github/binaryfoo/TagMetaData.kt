package io.github.binaryfoo

import io.github.binaryfoo.decoders.Decoders
import io.github.binaryfoo.decoders.PrimitiveDecoder
import io.github.binaryfoo.res.ClasspathIO
import io.github.binaryfoo.tlv.Tag
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.collections.*

/**
 * A set of rules for interpreting a set of tags.
 */

class TagMetaData(private val metadata: MutableMap<String, TagInfo>) {

  fun put(tag: Tag, tagInfo: TagInfo) {
    put(tag.hexString, tagInfo)
  }

  private fun put(tag: String, tagInfo: TagInfo) {
    if (metadata.put(tag, tagInfo) != null) {
      throw IllegalArgumentException("Duplicate entry for $tag")
    }
  }

  fun newTag(hexString: String, shortName: String, longName: String, primitiveDecoder: PrimitiveDecoder): Tag {
    val tag = Tag.fromHex(hexString)
    put(tag, TagInfo(shortName, longName, Decoders.PRIMITIVE, primitiveDecoder))
    return tag
  }

  fun newTag(hexString: String, shortName: String, longName: String, decoder: Decoder): Tag {
    val tag = Tag.fromHex(hexString)
    put(tag, TagInfo(shortName, longName, decoder, PrimitiveDecoder.HEX))
    return tag
  }

  fun get(tag: Tag): TagInfo {
    return metadata[tag.hexString]
        ?: return TagInfo("?", "?", Decoders.PRIMITIVE, PrimitiveDecoder.HEX)
  }

  fun join(other: TagMetaData): TagMetaData {
    val joined = copy(other)
    for ((tag, info) in metadata) {
      joined.put(tag, info)
    }
    return joined
  }

  companion object {
    @JvmStatic
    fun empty(): TagMetaData {
      return TagMetaData(HashMap())
    }

    @JvmStatic
    fun copy(metadata: TagMetaData): TagMetaData {
      return TagMetaData(HashMap(metadata.metadata))
    }

    @JvmStatic
    fun load(name: String): TagMetaData {
      val yaml = Yaml(SafeConstructor(LoaderOptions()))
      // snakeyaml 2.x parses numeric-looking keys (e.g. "82") as integers without a custom Resolver.
      // We stringify all keys and values after loading.
      @Suppress("UNCHECKED_CAST")
      val raw = yaml.load<Map<Any, Map<Any, Any?>>>(ClasspathIO.open(name))
      val map: Map<String, Map<String, String?>> = raw.entries.associate { (k, v) ->
        k.toString() to ((v as? Map<*, *>)?.entries?.associate { (vk, vv) -> vk.toString() to vv?.toString() } ?: emptyMap())
      }
      return TagMetaData(LinkedHashMap(map.mapValues { (_, props) ->
        val shortName = props["name"]!!
        val longName = props["longName"] ?: shortName
        val decoder: Decoder = if (props.contains("decoder")) {
          Class.forName("io.github.binaryfoo.decoders." + props["decoder"]).getDeclaredConstructor().newInstance() as Decoder
        } else {
          Decoders.PRIMITIVE
        }
        val primitiveDecoder = if (props.contains("primitiveDecoder")) {
          Class.forName("io.github.binaryfoo.decoders." + props["primitiveDecoder"]).getDeclaredConstructor().newInstance() as PrimitiveDecoder
        } else {
          PrimitiveDecoder.HEX
        }
        TagInfo(shortName, longName, decoder, primitiveDecoder, props["short"], props["long"])
      }))
    }

    fun toYaml(fileName: String, meta: TagMetaData, scheme: String) {
      PrintWriter(FileWriter(fileName)).use { writer ->
        for (e in meta.metadata.entries) {
          writer.println(e.key + ":")
          val tagInfo = e.value
          writer.println(" name: " + tagInfo.shortName)
          if (tagInfo.shortName != tagInfo.longName) {
            writer.println(" longName: " + tagInfo.longName)
          }
          if (tagInfo.decoder != Decoders.PRIMITIVE) {
            writer.println(" decoder: " + tagInfo.decoder.javaClass.simpleName)
          }
          if (tagInfo.primitiveDecoder != PrimitiveDecoder.HEX) {
            writer.println(" primitiveDecoder: " + tagInfo.primitiveDecoder.javaClass.simpleName)
          }
          writer.println(" scheme: $scheme")
          writer.println()
        }
      }
    }
  }
}

