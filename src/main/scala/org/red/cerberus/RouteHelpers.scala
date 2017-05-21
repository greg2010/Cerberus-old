package org.red.cerberus

import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

trait RouteHelpers
  extends LazyLogging
    with Middleware
    with AuthenticationHandler
    with FailFastCirceSupport

case class LegacySignupReq(key_id: Long,
                           verification_code: String,
                           name: String,
                           email: String,
                           password: String)