package org.red.cerberus

import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

trait RouteHelpers
  extends LazyLogging
    with FailFastCirceSupport

case class LegacySignupReq(key_id: Long,
                           verification_code: String,
                           name: String,
                           email: String,
                           password: String)

case class passwordResetRequestReq(email: String)

case class passwordChangeReq(new_password: String)

case class passwordChangeWithTokenReq(email: String, token: String, new_password: String)