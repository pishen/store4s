package store4s.sttp

import io.circe.Decoder
import io.circe.Printer
import sttp.client3.IsOption
import sttp.client3.circe._

package object circe {
  implicit val printerDrop: Printer =
    Printer.noSpaces.copy(dropNullValues = true)
  implicit def deserializer[B: Decoder: IsOption]: BodyDeserializer[B] =
    BodyDeserializer.from(asJson[B])
}
