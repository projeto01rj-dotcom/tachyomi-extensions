package eu.kanade.tachiyomi.extension.pt.leitordemangas

import eu.kanade.tachiyomi.multisrc.aurora.Aurora
import keiyoushi.annotation.Source

/**
 * Leitor público do site Leitor de Mangas.
 * Não cria sessão de usuário nem exige assinatura; usa somente o fluxo de leitura
 * disponibilizado pelo próprio site para visitantes.
 */
@Source
abstract class LeitorDeMangas : Aurora()
