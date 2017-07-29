package org.red.cerberus.http


case class LegacySignupReq(keyId: Long,
                           verificationCode: String,
                           name: String,
                           email: String,
                           password: String)

case class SSOLoginReq(authCode: String)

case class PasswordLoginReq(login: String, password: String)

case class passwordResetRequestReq(email: String)

case class passwordChangeReq(newPassword: String)

case class passwordChangeWithTokenReq(email: String, token: String, newPassword: String)