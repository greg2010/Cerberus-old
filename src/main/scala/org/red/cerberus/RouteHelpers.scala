package org.red.cerberus

import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

trait RouteHelpers
  extends LazyLogging
    with Middleware
    with AuthenticationHandler
    with Responses
    with FailFastCirceSupport