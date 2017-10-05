package com.softwaremill.bootzooka.common.crypto

import com.typesafe.scalalogging.StrictLogging
import de.mkammerer.argon2.Argon2Factory.Argon2Types
import de.mkammerer.argon2.{Argon2, Argon2Factory}

class Argon2dPasswordHashing(config: CryptoConfig) extends PasswordHashing with StrictLogging {
  private val argon2: Argon2 = Argon2Factory.create(Argon2Types.ARGON2d)

  def hashPassword(password: String, salt: String): String =
    argon2.hash(config.iterations, config.memory, config.parallelism, salt + password)

  def verifyPassword(hash: String, password: String, salt: String): Boolean =
    argon2.verify(hash, salt + password)

  def requiresRehashing(hash: String): Boolean = {
    val hashParams   = hash.split('$')(3)
    val configParams = s"m=${config.memory},t=${config.iterations},p=${config.parallelism}"
    configParams != hashParams
  }
}
