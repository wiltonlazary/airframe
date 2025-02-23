/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.airframe.http

import wvlet.airframe.codec.{MessageCodec, MessageCodecFactory}

trait HttpServerExceptionBase {
  // WARNING: Using a self reference hits compiler VerifyError https://github.com/lampepfl/dotty/issues/9270
  // self: HttpServerException =>

  private def self: HttpServerException = this.asInstanceOf[HttpServerException]

  inline def withJsonOf[A](a: A): HttpServerException = {
    self.withJson(MessageCodec.of[A].toJson(a))
  }
  inline def withJsonOf[A](a: A, codecFactory: MessageCodecFactory): HttpServerException = {
    self.withJson(codecFactory.of[A].toJson(a))
  }
  inline def withMsgPackOf[A](a: A): HttpServerException = {
    self.withMsgPack(MessageCodec.of[A].toMsgPack(a))
  }
  inline def withMsgPackOf[A](a: A, codecFactory: MessageCodecFactory): HttpServerException = {
    self.withMsgPack(codecFactory.of[A].toMsgPack(a))
  }

  /**
    * Set the content body using a given object. Encoding can be JSON or MsgPack based on Content-Type header.
    */
  inline def withContentOf[A](a: A): HttpServerException = {
    if (self.isContentTypeMsgPack) {
      self.withMsgPack(MessageCodec.of[A].toMsgPack(a))
    } else {
      self.withJson(MessageCodec.of[A].toJson(a))
    }
  }

  /**
    * Set the content body using a given object and codec factory. Encoding can be JSON or MsgPack based on Content-Type
    * header.
    */
  inline def withContentOf[A](a: A, codecFactory: MessageCodecFactory): HttpServerException = {
    if (self.isContentTypeMsgPack) {
      self.withMsgPack(codecFactory.of[A].toMsgPack(a))
    } else {
      self.withJson(codecFactory.of[A].toJson(a))
    }
  }
}
