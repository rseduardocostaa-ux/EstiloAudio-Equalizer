package com.estiloaudio.equalizer

/** Textos da ajuda (botão "?"): o que faz, mais dois exemplos para comparar. */
class HelpTopic(
    val title: String,
    val what: String,
    val ex1Label: String,
    val ex1: String,
    val ex2Label: String,
    val ex2: String
)

object HelpTexts {

    fun get(key: String): HelpTopic? = TOPICS[key]

    private val TOPICS: Map<String, HelpTopic> = mapOf(

        "comp" to HelpTopic(
            "O que é o compressor?",
            "O compressor diminui a diferença entre os sons fortes e os fracos. Quando o volume passa de um limite (o Threshold), ele abaixa só a parte que passou. O resultado é um som mais uniforme, e você pode aumentar o volume geral sem que os trechos mais fortes estourem.",
            "Exemplo 1 · Compressor desligado (Ratio Off)",
            "Numa música com batida forte e voz baixa, a batida cobre a voz. Você fica mexendo no volume: abaixa na parte forte e aumenta na parte fraca.",
            "Exemplo 2 · Compressor ligado",
            "A batida é segurada e a voz fica mais presente. O volume fica parecido do começo ao fim, bom para ouvir na rua ou no ônibus. Em casa, com som de qualidade, deixe desligado para manter a música como foi gravada."
        ),

        "multiband" to HelpTopic(
            "Compressor em 3 faixas",
            "Em vez de comprimir o som inteiro de uma vez, ele divide o som em graves, médios e agudos e comprime cada parte separadamente, cada uma com seus próprios ajustes. Os dois \"Cortes\" definem onde uma faixa termina e a outra começa.",
            "Exemplo 1 · Desligado (1 faixa só)",
            "Um grave forte, como o bumbo, faz o compressor abaixar o som inteiro. A voz e os agudos \"afundam\" a cada batida, e o som parece pulsar.",
            "Exemplo 2 · Ligado (3 faixas)",
            "Só os graves são comprimidos. A voz e os agudos continuam intactos, então dá para controlar um grave exagerado sem tirar o brilho da música."
        ),

        "curve" to HelpTopic(
            "Gráfico do compressor",
            "Mostra o que entra no compressor (embaixo) e o que sai (do lado). Uma linha reta na diagonal significa que nada muda. A linha laranja pontilhada marca o Threshold: depois dela a linha \"dobra\" para baixo, e é aí que o compressor age. O ponto mostra o nível da música neste momento.",
            "Exemplo 1 · Ratio Off",
            "A linha fica reta na diagonal. Entra um som de -10 dB e sai os mesmos -10 dB: nada foi comprimido.",
            "Exemplo 2 · Threshold -18 dB e Ratio 4:1",
            "Até -18 dB a linha é reta. Depois ela dobra: um som que entra a -6 dB (12 dB acima do limite) sai a -15 dB (só 3 dB acima do limite)."
        ),

        "attack" to HelpTopic(
            "Attack (ataque)",
            "É o tempo que o compressor demora para começar a abaixar o volume depois que o som passa do limite. Fica em milissegundos (ms): 1 ms é quase instantâneo.",
            "Exemplo 1 · Attack curto (1 a 5 ms)",
            "Ele pega a pancada na hora. O som fica mais \"preso\" e controlado, mas perde um pouco do impacto no começo de cada batida.",
            "Exemplo 2 · Attack longo (30 a 100 ms)",
            "Ele deixa passar o início da batida (o \"tum\") e só depois segura. O som fica com mais impacto e mais vivo."
        ),

        "release" to HelpTopic(
            "Release (soltura)",
            "É o tempo que o compressor demora para soltar e deixar o volume voltar ao normal quando o som volta a ficar baixo.",
            "Exemplo 1 · Release curto (20 a 50 ms)",
            "O volume volta rápido. O som fica mais vivo, mas em ajuste forte pode dar um efeito de \"pulsar\" ou \"bombear\".",
            "Exemplo 2 · Release longo (300 a 800 ms)",
            "O volume volta devagar. O som fica mais suave e natural, mas em música rápida pode parecer um pouco abafado."
        ),

        "ratio" to HelpTopic(
            "Ratio (proporção)",
            "É a força da compressão. Com 2:1, a cada 2 dB que o som passa do Threshold, só 1 dB passa para a saída. \"Off\" (1:1) é desligado. Quanto maior o número, mais forte a compressão.",
            "Exemplo 1 · Ratio 2:1 (leve)",
            "O som fica mais uniforme sem parecer \"apertado\". É um bom ajuste para o dia a dia.",
            "Exemplo 2 · Ratio 10:1 ou mais (forte)",
            "Tudo que passa do limite fica achatado, quase como um teto de volume. Protege o ouvido, mas tira a vida e a dinâmica da música."
        ),

        "threshold" to HelpTopic(
            "Threshold (limite)",
            "É o nível de volume a partir do qual o compressor começa a agir. 0 dB é o volume máximo, e números negativos são mais baixos. Só o que passa desse limite é comprimido.",
            "Exemplo 1 · Threshold -6 dB (alto)",
            "Quase nada passa de um limite tão alto. O compressor só age nos picos mais fortes, e o efeito é discreto.",
            "Exemplo 2 · Threshold -30 dB (baixo)",
            "Quase toda a música passa do limite. O compressor age o tempo todo, e o som fica bem uniforme e \"comprimido\"."
        ),

        "gain" to HelpTopic(
            "Gain (ganho de saída)",
            "É o volume que se soma depois da compressão. Como o compressor abaixa os trechos fortes, o som final pode ficar mais baixo. Este controle devolve esse volume.",
            "Exemplo 1 · Gain 0 dB",
            "Nada é somado. Com compressão forte, o som pode ficar mais baixo do que era antes.",
            "Exemplo 2 · Gain +6 dB",
            "Compensa a perda: o som fica mais alto e encorpado. Cuidado: ganho demais pode distorcer, e o Limiter de saída ajuda a evitar isso."
        ),

        "crossover" to HelpTopic(
            "Cortes (Corte 1 e Corte 2)",
            "São os pontos onde o som é dividido no compressor de 3 faixas. O Corte 1 separa os graves dos médios (de 60 Hz a 1 kHz). O Corte 2 separa os médios dos agudos (de 1 kHz a 12 kHz).",
            "Exemplo 1 · Corte 1 em 100 Hz",
            "A faixa de graves pega só o grave profundo (bumbo e baixo). A voz e os instrumentos ficam livres, na faixa dos médios.",
            "Exemplo 2 · Corte 1 em 400 Hz",
            "A faixa de graves pega também o \"corpo\" da voz e do violão. A compressão age sobre mais coisas, e o som fica mais contido."
        ),

        "balance" to HelpTopic(
            "Balance",
            "Equilibra o volume entre o lado esquerdo e o direito. \"Centro\" deixa os dois lados iguais.",
            "Exemplo 1 · Centro",
            "Os dois lados tocam com o mesmo volume. É o ajuste normal.",
            "Exemplo 2 · R 30 (ou L 30)",
            "Com R 30, o lado esquerdo fica mais baixo e o som \"vai\" para a direita. Com L 30 é o contrário. É útil se um fone toca mais baixo que o outro."
        ),

        "volume" to HelpTopic(
            "Volume de saída",
            "É o volume final do efeito, de -24 a +6 dB. Ele é somado ao ganho depois do compressor e mexe no som que chega ao fone ou à caixa.",
            "Exemplo 1 · Volume -6 dB",
            "O som fica mais baixo, mas ganha folga contra distorção. É bom quando você reforçou muito os graves no equalizador.",
            "Exemplo 2 · Volume +3 dB",
            "O som fica mais alto. Fique de olho no medidor de pico e no limiter para não estourar."
        ),

        "limiter" to HelpTopic(
            "Limiter de saída",
            "É uma trava de segurança: nada passa do nível definido no Threshold. Evita estouro e distorção e protege a caixa e o ouvido.",
            "Exemplo 1 · Limiter ligado (-1 dB)",
            "Os picos são segurados antes de distorcer. O som continua limpo mesmo com os graves bem reforçados no equalizador.",
            "Exemplo 2 · Limiter desligado",
            "O som passa livre. Se o equalizador e o ganho somarem mais que 0 dB, o som estoura (clipping) e fica distorcido."
        ),

        "lim_thr" to HelpTopic(
            "Threshold do limiter",
            "É o teto de volume do limiter. -1 dB é quase o máximo possível. Quanto mais baixo o valor, mais baixo é o teto.",
            "Exemplo 1 · -1 dB (teto alto)",
            "Preserva o volume e só segura picos extremos. É o ajuste de segurança comum.",
            "Exemplo 2 · -8 dB (teto baixo)",
            "O som fica mais contido e mais achatado. É útil para ouvir baixinho à noite, protegendo o ouvido."
        ),

        "lim_rel" to HelpTopic(
            "Release do limiter",
            "É o tempo que o limiter leva para soltar depois de segurar um pico.",
            "Exemplo 1 · Release curto (5 a 20 ms)",
            "Solta rápido e mantém o volume alto, mas pode causar uma leve distorção nos graves.",
            "Exemplo 2 · Release longo (200 a 500 ms)",
            "Solta devagar e o som fica mais suave. Em compensação, o volume cai por um instante depois de cada pico forte."
        ),

        "band_type" to HelpTopic(
            "Pico, Grave e Agudo",
            "Cada banda do equalizador pode agir de 3 jeitos. PICO mexe numa região em volta da frequência escolhida, como um \"morrinho\". GRAVE mexe em tudo que está abaixo da frequência escolhida. AGUDO mexe em tudo que está acima dela.",
            "Exemplo 1 · Pico em 1 kHz, +4 dB",
            "Realça só a região da voz, em volta de 1 kHz. Os graves e os agudos ficam como estavam.",
            "Exemplo 2 · Grave em 100 Hz, +4 dB",
            "Reforça todo o grave de 100 Hz para baixo (bumbo e baixo), como se levantasse o \"chão\" do som. Com Agudo em 8 kHz e -4 dB acontece o contrário em cima: todos os agudos acima de 8 kHz ficam mais suaves, e o chiado diminui."
        ),

        "freq" to HelpTopic(
            "Freq (frequência)",
            "É a frequência da banda, o ponto do som onde o ajuste acontece. Graves ficam de 20 a 200 Hz, médios de 200 Hz a 5 kHz e agudos acima disso.",
            "Exemplo 1 · Freq 60 Hz",
            "Mexe no \"peso\" da música, o bumbo e o baixo.",
            "Exemplo 2 · Freq 3 kHz",
            "Mexe na clareza e na presença da voz."
        ),

        "gain_band" to HelpTopic(
            "Gain da banda",
            "Diz quanto a banda reforça (valores positivos) ou corta (valores negativos) a frequência escolhida, em dB. Zero não muda nada.",
            "Exemplo 1 · +4 dB em 60 Hz",
            "Mais grave: o som fica mais encorpado e pesado.",
            "Exemplo 2 · -4 dB em 3 kHz",
            "Voz menos dura e menos agressiva. É bom para música que cansa o ouvido."
        ),

        "q" to HelpTopic(
            "Q (largura da banda)",
            "O Q define quão larga é a região afetada. Q baixo é largo: o ajuste é suave e pega muitas frequências ao redor. Q alto é estreito: o ajuste é preciso e pega quase só a frequência escolhida. Nos tipos Grave e Agudo, o Q muda a suavidade da transição.",
            "Exemplo 1 · Q 0,5 (largo)",
            "+4 dB em 1 kHz levanta uma região grande em volta, de uns 300 Hz a 3 kHz. O som muda de forma natural, bom para ajustar o timbre.",
            "Exemplo 2 · Q 5 (estreito)",
            "-6 dB em 2,5 kHz tira só um \"apito\" ou chiado específico, sem mexer no resto da música."
        )
    )
}
