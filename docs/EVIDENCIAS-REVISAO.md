# Revisão profunda de CloudForge

Revisão de 22/11/2026. Li as sete páginas do enunciado oficial `Aula13_Trabalho gRPC client-side load balancing.docx.pdf`, inclusive os exemplos das páginas 5 a 7. O PDF prevalece sobre os comentários e a documentação do projeto. A auditoria abaixo foi feita antes das correções. As referências desta primeira tabela são as linhas da versão original, preservada na cópia `scratchpad/astra/antes`; as referências da revisão final aparecem ao final.

## Auditoria item a item do PDF

Abreviações dos arquivos, sempre dentro de `src/main/java/com/mycompany` do projeto indicado:

- `Core`: `ClienteCloudForge/clientecloudforge/ClienteCore.java`.
- `Console`: `ClienteCloudForge/clientecloudforge/ClienteCloudForge.java`.
- `Impl`: `ServidorCloudForge/servidorcloudforge/CloudForgeServiceImpl.java`.
- `Servidor`: `ServidorCloudForge/servidorcloudforge/ServidorCloudForge.java`.
- `Nomes`: `ServicoNomes/servicodenomes/NomesServiceImpl.java`.
- `InicioNomes`: `ServicoNomes/servicodenomes/ServicoNomes.java`.

| Item do PDF | Situação encontrada antes de corrigir | Evidência no código original |
|---|---|---|
| 2: servidor Java, duas instâncias | Cumpre: porta recebida por argumento; cada processo tem memória própria. | Servidor:29,47,58; ServidorCloudForge/pom.xml:13 |
| 2: registro e descoberta | Cumpre no fluxo normal; mudança de endereço com mesmo ID não é aplicada no cliente. | Servidor:69; Nomes:46,71; Core:73,87 |
| 2: Health padrão | Cumpre: serviço registrado e estados geral e específico atualizados. | Servidor:57,59,86 |
| 2: rodízio manual e Health antes da tarefa | Parcial: funciona sequencialmente; a varredura pode repetir candidatos sob concorrência e a atualização fecha canais ainda selecionados. | Core:103,112,143 |
| 2: proteção do estado entre threads | Servidor e nomes cumprem; ciclo de vida da lista/canais no cliente precisa correção. | Impl:81,124,167,188,197; Nomes:31,35,39,84,100; Core:73,236 |
| 2: deadline e tratamento | Cumpre, com limitações no diagnóstico do teste de concorrência. | Core:77,119,158,192,198,205,209 |
| 3.1: Java | Cumpre, release 26 e gRPC 1.75.0. | ServidorCloudForge/pom.xml:13,15 |
| 3.1: serviço em proto | Cumpre: ExecutarTarefa, ObterEstatisticas e ObterHistorico. | ServidorCloudForge/src/main/proto/cloudforge.proto:9 |
| 3.1: instâncias em portas distintas | Cumpre: porta não é fixa no servidor. | Servidor:47,58 |
| 3.2: recurso compartilhado local | Cumpre: contador, IDs, vagas e histórico da mesma instância protegidos. | Impl:46,81,124,167,188,197 |
| 3.2: memória de cada processo | Cumpre: nova implementação por processo; IDs locais podem repetir entre instâncias. | Servidor:56; Impl:47 |
| 3.2: compartilhamento global opcional | Existe com --global, centralizado no nomes; não oferece transação nem tolerância a falhas. Não é requisito obrigatório. | Impl:143; Nomes:83,98 |
| 3.3: HealthStatusManager e dependência | Cumpre: grpc-services, Health padrão e SERVING/NOT_SERVING. | ServidorCloudForge/pom.xml:37; Servidor:57,86,114,118 |
| 3.4: registro ao iniciar | Cumpre se nomes estiver disponível; falha de registro só gera aviso e não há re-registro automático. | Servidor:67 |
| 3.4: descobrir sem portas fixas de aplicação | Cumpre: somente o endereço de bootstrap do nomes tem padrão configurável. | Core:43,67,73; Console:14 |
| 3.4: papéis separados | Cumpre: nomes lista, Health verifica prontidão, cliente escolhe. | Nomes:71; Servidor:86; Core:112 |
| 3.5: balanceamento feito pelo grupo | Cumpre: não usa política automática do gRPC. | Core:103,112 |
| 3.5: um canal e stub por servidor, reutilizados | Parcial: reutiliza por ID, mas ignora novo host/porta e atualizações simultâneas podem perder canais. | Core:54,59,81 |
| 3.5: índice circular | Parcial: sem índice fora da faixa demonstrado, mas o tamanho sozinho não identifica a geração da lista; threads podem consumir candidatos da mesma varredura. | Core:103,112 |
| 3.5: Health com deadline, fora do lock | Cumpre. | Core:104,119 |
| 3.5: pular NOT_SERVING, erro, deadline | Cumpre no caso sequencial; a varredura concorrente não garante visitar todos. | Core:112 |
| 3.5: avisar quando nenhum saudável | Cumpre no caso normal; há falso negativo durante atualização da lista. | Core:145,150 |
| 3.5: região crítica curta | Cumpre para o índice. Nenhum RPC ou sleep dentro de synchronized nos arquivos de negócio examinados. | Core:103; Impl:81,124,167,188,197; Nomes:31,35,39,84,100 |
| 4: deadline em chamadas importantes | Cumpre: Health 2 s; tarefa 5 s; descoberta/consultas 3 s; registro/desregistro/global do servidor 2 s. | Core:77,119,158,192,198,205; Servidor:69,95; Impl:145 |
| 4: capturar erro e informar | Cumpre: mensagens próprias e retry limitado; INVALID_ARGUMENT não repete. | Core:165,180 |
| 4: continuar com instância restante | Cumpre no fluxo usual; retry pode escolher novamente a instância anterior sob concorrência. | Core:171 |
| 6: subir nomes e duas instâncias, mostrar registro | Implementado; requer processos reais na demonstração. | InicioNomes:12; Servidor:64,69 |
| 6: cliente mostrar lista descoberta | Implementado. | Core:95; Console:26 |
| 6: seis chamadas alternadas | Implementado por rr 6. | Console:51; Core:103 |
| 6: NOT_SERVING e volta a SERVING | Implementado por comandos do servidor. | Servidor:114,118 |
| 6: queda com cliente aberto | Implementado via Health e retry. | Core:119,165 |
| 6: nenhum saudável | Implementado, ressalvado o falso negativo acima. | Core:150 |
| 6: atraso maior que deadline | Implementado por atraso 7000 e deadline 5 s. | Servidor:122; Impl:103; Core:158 |
| 6: concorrência sem IDs repetidos/perdidos | Implementado por concorrencia 200, mas soma agregada não é válida se uma instância desaparecer entre as duas leituras. | Core:209,212,230 |
| 6: estado local versus global | Implementado: resposta identifica instância e ID local; bônus é histórico central, não ID global. | Impl:124,156; Nomes:83 |

## Achados e correções mínimas planejadas

1. **Alta, ciclo de vida do cliente:** `Core:73,94,236`. Uma atualização remove um servidor entre Health e ExecutarTarefa; shutdown permite terminar RPC já iniciada, mas impede a próxima. Duas atualizações podem publicar listas construídas da mesma versão e abandonar canais, e atualizar depois de fechar reabre recursos. Correção: reservar a lista enquanto a operação a usa, adiar fechamento dos removidos até o último uso, publicar apenas se a versão ainda for atual e impedir atualização após fechar. Manter rede e fechamento fora do lock.
2. **Média, endereço obsoleto:** `Core:87`. Registrar o mesmo ID com outra porta mantém o stub antigo. Correção: reutilizar somente quando ID, host e porta coincidirem.
3. **Média, varredura e retry:** `Core:112,171`. Duas threads podem avançar o índice entre os passos de uma varredura; uma consulta visita o mesmo servidor indisponível duas vezes e perde o saudável. O retry também pode cair no servidor que acabou de falhar. Correção: reservar um ponto de partida e percorrer circularmente a mesma cópia uma vez, excluindo a instância já tentada no retry.
4. **Média, dados inválidos:** `Impl:259`. split descarta elementos vazios finais: `1,` é aceito e `,` chega ao processamento com vetor vazio, dando INTERNAL. NaN e Infinity também passam como números. Correção: preservar elementos vazios com split de limite -1 e rejeitar números não finitos.
5. **Média, medição inválida após queda:** `Core:209`. A soma final omite o contador da instância caída, podendo produzir delta negativo ou até um OK por coincidência. Correção: comparar amostras por instância, sinalizar medição inconclusiva se faltarem amostras ou os contadores recuarem e preservar a contagem de respostas recebidas pelo cliente.
6. **Baixa, parâmetros negativos:** `Impl:58,66,67`. limite negativo ou zero deixa tudo rejeitado; atraso negativo é aceito como se fosse zero. Correção: validar limite positivo e atraso não negativo, inclusive na construção.
7. **Baixa, encerramento:** `Servidor:92`. Desregistro pode esperar 2 s enquanto o servidor continua anunciando SERVING. Correção: marcar NOT_SERVING antes do RPC de desregistro; completar limpeza no hook com espera limitada e fechar canal de nomes.
8. **Baixa, mensagem incorreta:** `Core:79`. Falha do nomes informa lista vazia mesmo preservando a lista anterior. Correção: informar que a lista anterior foi mantida.

Não encontrei vazamento normal de `emAndamento`: a admissão incrementa sob lock, a conclusão decrementa uma vez e o finally cobre erro/cancelamento. `atrasoMs` e `limite` eram volatile: leitura fora do lock não é, por si só, data race. Contadores e histórico local usam o mesmo lock; total global e histórico global usam o monitor de historico. Construção de mensagens e cópias limitadas dentro do lock não incluem RPC, sleep, hash, fatorial ou ordenação. `CorridaDemo` contém corrida proposital, isolada do serviço.

Fatorial 0 e 5000 estão previstos, negativos e maiores que 5000 são rejeitados. Espaços em listas são aparados; valores negativos nas listas são permitidos. Texto gigante deve ser verificado contra o limite de mensagem do transporte, sem inventar um novo limite de negócio. Reinício com o mesmo ID reinicia contadores locais; não há identificador de geração no proto, portanto uma prova absoluta de continuidade após reinício exigiria ampliar o contrato. Histórico global tem perda possível quando nomes cai e é mantido em memória; isso é uma limitação documentada do bônus, não uma transação distribuída.

## Execução e resultados

Compilação feita somente nas cópias `antes` e `depois`, em:

`(pasta temporária de testes)`

Os três `mvn -q package` e `mvn -q dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"` terminaram com código 0 na versão testada. JAVA_HOME apontou para `C:\Program Files\Apache NetBeans\jdk`; Maven veio de `C:\Program Files\Apache NetBeans\java\maven\bin`. A primeira cópia da GUI em edição apresentou `Animador.java:76 illegal forward reference`; uma nova leitura do arquivo já corrigido pelo outro agente permitiu compilar o cliente completo. Não alterei arquivos da GUI para resolver isso.

Nomes, A, B e cliente console rodaram em processos Java separados, apenas em 50150, 50151 e 50152. Os testes foram conduzidos por `run_review.py`; `ReviewProbe.java` usa os mesmos stubs e o ClienteCore, sem frameworks extras. Logs integrais e sondas estão na pasta acima: `antes.log`, `antes-probes.log`, `depois.log` e `depois-probes.log`. A primeira captura das sondas teve perda de acentos na decodificação do stdout; as sondas anteriores à correção foram repetidas com stdout UTF-8 explícito e são as transcritas abaixo.

Comandos de reprodução na pasta de cópias:

```powershell
python -X utf8 run_review.py antes
python -X utf8 run_review.py antes probes
python -X utf8 run_review.py depois
python -X utf8 run_review.py depois probes
```

Executar um comando de cada vez, com as três portas livres. O script encerra somente os processos que iniciou. Os trechos seguintes são extraídos dos logs reais; omiti avisos repetitivos do JDK, saída de inicialização das sondas e muitas linhas de tarefas/rejeições. Nos deltas antigos, citei apenas o prefixo numérico da linha.

### Antes/depois: validação e configurações

Antes:
```text
DADOS ',': ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Falha na tarefa (INTERNAL)., codigoErro=INTERNAL]
DADOS '1,': ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=1, resultado=soma=1, média=1, mínimo=1, máximo=1, ordenada=[1], duracaoMs=2, erro=, codigoErro=]
DADOS 'NaN,1': ResultadoTarefa[ok=true, instancia=srv-50151, idTarefa=1, resultado=soma=NaN, média=NaN, mínimo=1, máximo=NaN, ordenada=[1, NaN], duracaoMs=2, erro=, codigoErro=]
DADOS 'Infinity,1': ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=2, resultado=soma=Infinity, média=Infinity, mínimo=1, máximo=Infinity, ordenada=[1, Infinity], duracaoMs=0, erro=, codigoErro=]
```

```text
[A] Limite de tarefas simultâneas: -2
[A] Atraso simulado: -1 ms
```

Depois:
```text
DADOS ',': ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
DADOS '1,': ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
DADOS 'NaN,1': ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
DADOS 'Infinity,1': ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
```

```text
[A] Comando inválido: limite deve ser positivo
[A] Comando inválido: atraso não pode ser negativo
```

### Antes/depois: endereço, fechamento e rodízio

A sonda endpoint remove B da descoberta, registra o ID de A apontando para a porta de B e atualiza o cliente. A sonda closed chama fechar e depois atualizar. A sonda race retira B da descoberta dentro do callback de log de NOT_SERVING de A, mantendo B ligado: essa intercalação deixa explícita a troca de lista no meio da escolha. A sonda interleave executa outro envio dentro desse callback para reproduzir, de forma determinística, um avanço de índice que outra thread também poderia causar. O stress posterior usa threads reais.

Antes:
```text
Mesmo ID, nova porta: ResultadoTarefa[ok=true, instancia=srv-50151, idTarefa=4, resultado=b6bf7bc8d96f3ea9d132c83b3da8e7760e420138485657372db4d6a981d3fd9e, duracaoMs=0, erro=, codigoErro=]
IDs depois de fechar + atualizar: [srv-50151, srv-50152]
Lista atualizada durante o rodízio: [srv-50151]
Atualização durante Health: ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Nenhum servidor saudável no momento. Tente novamente., codigoErro=UNAVAILABLE]
Envio intercalado: true
Envio intercalado: true
Envio intercalado: true
Envio intercalado: true
Varredura com avanço intercalado: ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Nenhum servidor saudável no momento. Tente novamente., codigoErro=UNAVAILABLE]
```

Depois:
```text
Mesmo ID, nova porta: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=3, resultado=b6bf7bc8d96f3ea9d132c83b3da8e7760e420138485657372db4d6a981d3fd9e, duracaoMs=0, erro=, codigoErro=]
IDs depois de fechar + atualizar: []
Lista atualizada durante o rodízio: [srv-50151]
Atualização durante Health: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=4, resultado=129ce50dd90bf244858763d3f10932a9f6d8a521ad4f2c946574e9a566e04054, duracaoMs=0, erro=, codigoErro=]
Envio intercalado: true
Varredura com avanço intercalado: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=6, resultado=69e03750027e6b1e9f95bd21d227613c0024ec799ebfb7fe281371e1153f9bda, duracaoMs=0, erro=, codigoErro=]
```

No teste adicional de ciclo de vida, uma tarefa ficou 1500 ms em processamento enquanto ambas as instâncias foram retiradas da lista. O cliente foi fechado antes de a tarefa terminar. Inspecionei os ManagedChannel reservados pela tarefa e aguardei sua terminação:

```text
Canais fechados enquanto tarefa usa lista: 0
Tarefa em andamento após fechar: true
Todos os canais retirados terminaram: true
Lista após fechamento concorrente: []
```

```text
120 atualizações + 120 envios paralelos: falhas=0
Fechado após stress: []
```

### Registro, descoberta, rr 6 e estado local

```text
[NOMES] [NOMES] Serviço de nomes ouvindo na porta 50150
[NOMES] [NOMES] Registrada: srv-50151 em localhost:50151
[NOMES] [NOMES] Registrada: srv-50152 em localhost:50152
```

```text
[CLIENTE] Instâncias encontradas no serviço de nomes: 2
[CLIENTE]   srv-50151  localhost:50151
[CLIENTE]   srv-50152  localhost:50152
```

```text
COMANDO CLIENTE: rr 6
[CLIENTE] > Tarefa #3 respondida por srv-50151: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] Tarefa #7 respondida por srv-50152: 1! = 1 (0 ms)
[CLIENTE] Tarefa #4 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] Tarefa #8 respondida por srv-50152: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
[CLIENTE] Tarefa #5 respondida por srv-50151: 4! = 24 (0 ms)
[CLIENTE] Tarefa #9 respondida por srv-50152: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (2 ms)
```

Na sonda de entradas executada com instâncias novas, A respondeu seu #1 ao resumo de dados e B respondeu seu #1 ao fatorial de zero. Isso mostra IDs independentes por processo:

```text
DADOS ' 3, -1, 2.5 ': ResultadoTarefa[ok=true, instancia=srv-50151, idTarefa=1, resultado=soma=4.5, média=1.5, mínimo=-1, máximo=3, ordenada=[-1, 2.5, 3], duracaoMs=2, erro=, codigoErro=]
CALCULO 0: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=1, resultado=0! = 1, duracaoMs=0, erro=, codigoErro=]
```

### NOT_SERVING, retorno e nenhum saudável

```text
COMANDO B: notserving
[B] Health: NOT_SERVING
COMANDO CLIENTE: rr 6
[CLIENTE] > Tarefa #6 respondida por srv-50151: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] [CLIENTE] srv-50152 pulado (NOT_SERVING)
[CLIENTE] Tarefa #7 respondida por srv-50151: 1! = 1 (0 ms)
[CLIENTE] Tarefa #8 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] [CLIENTE] srv-50152 pulado (NOT_SERVING)
[CLIENTE] Tarefa #9 respondida por srv-50151: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
[CLIENTE] Tarefa #10 respondida por srv-50151: 4! = 24 (0 ms)
[CLIENTE] [CLIENTE] srv-50152 pulado (NOT_SERVING)
[CLIENTE] Tarefa #11 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
COMANDO B: serving
[B] Health: SERVING
COMANDO CLIENTE: rr 6
[CLIENTE] > Tarefa #12 respondida por srv-50151: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] Tarefa #10 respondida por srv-50152: 1! = 1 (0 ms)
[CLIENTE] Tarefa #13 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] Tarefa #11 respondida por srv-50152: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
[CLIENTE] Tarefa #14 respondida por srv-50151: 4! = 24 (0 ms)
[CLIENTE] Tarefa #12 respondida por srv-50152: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
COMANDO A: notserving
[A] Health: NOT_SERVING
COMANDO B: notserving
[B] Health: NOT_SERVING
COMANDO CLIENTE: enviar HASH nenhum
[CLIENTE] > [CLIENTE] srv-50151 pulado (NOT_SERVING)
[CLIENTE] [CLIENTE] srv-50152 pulado (NOT_SERVING)
[CLIENTE] [CLIENTE] srv-50152 pulado (NOT_SERVING)
[CLIENTE] [CLIENTE] srv-50151 pulado (NOT_SERVING)
[CLIENTE] Nenhum servidor saudável no momento. Tente novamente.
COMANDO A: serving
[A] Health: SERVING
COMANDO B: serving
[B] Health: SERVING
```

### Deadline maior que 5 s e liberação de vagas

```text
COMANDO A: atraso 7000
[A] Atraso simulado: 7000 ms
COMANDO CLIENTE: rr 2
[CLIENTE] > [CLIENTE] srv-50151 DEADLINE_EXCEEDED (Tempo limite excedido; tente novamente.) Tentando o próximo servidor saudável.
[CLIENTE] Tarefa #13 respondida por srv-50152: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[A] [srv-50151] CANCELADA cliente=c3 (cliente desistiu ou deadline estourou)
[CLIENTE] [CLIENTE] srv-50151 DEADLINE_EXCEEDED (Tempo limite excedido; tente novamente.) Tentando o próximo servidor saudável.
[CLIENTE] Tarefa #14 respondida por srv-50152: 1! = 1 (0 ms)
COMANDO A: atraso 0
[A] Atraso simulado: 0 ms
[A] [srv-50151] CANCELADA cliente=c3 (cliente desistiu ou deadline estourou)
COMANDO CLIENTE: stats
[CLIENTE] > srv-50151: recebidas=16 concluídas=14 rejeitadas=0 em andamento=0 limite=4
[CLIENTE] srv-50152: recebidas=14 concluídas=14 rejeitadas=0 em andamento=0 limite=4
```

O cancelamento não representa rollback. O teste demonstra a liberação das vagas no caso de espera cancelada; uma conclusão concorrente ao deadline ainda pode aparecer no histórico sem resposta recebida pelo cliente.

### Concorrência 200, limite 4 e limite 2

```text
COMANDO CLIENTE: concorrencia 200
[CLIENTE] > Enviadas: 200, concluídas: 200, rejeitadas: 0, outras falhas: 0
[CLIENTE] IDs de tarefa repetidos por instância: 0
[CLIENTE] Soma concluídas nas instâncias (delta): 200 - OK
COMANDO A: limite 2
[A] Limite de tarefas simultâneas: 2
COMANDO B: limite 2
[B] Limite de tarefas simultâneas: 2
COMANDO A: atraso 100
[A] Atraso simulado: 100 ms
COMANDO B: atraso 100
[B] Atraso simulado: 100 ms
COMANDO CLIENTE: concorrencia 200
[CLIENTE] Enviadas: 200, concluídas: 8, rejeitadas: 192, outras falhas: 0
[CLIENTE] IDs de tarefa repetidos por instância: 0
[CLIENTE] Soma concluídas nas instâncias (delta): 8 - OK
COMANDO CLIENTE: stats
[CLIENTE] > srv-50151: recebidas=120 concluídas=118 rejeitadas=192 em andamento=0 limite=2
[CLIENTE] srv-50152: recebidas=118 concluídas=118 rejeitadas=192 em andamento=0 limite=2
COMANDO A: limite 32
[A] Limite de tarefas simultâneas: 32
COMANDO B: limite 32
[B] Limite de tarefas simultâneas: 32
COMANDO A: atraso 100
[A] Atraso simulado: 100 ms
COMANDO B: atraso 100
[B] Atraso simulado: 100 ms
```

No segundo teste usei atraso de 100 ms para manter as duas vagas ocupadas e produzir rejeições de forma observável. As rejeições dos servidores contam tentativas, enquanto as rejeições no cliente contam pedidos que falharam ao final do retry. Todas as tarefas admitidas terminaram e emAndamento voltou a zero.

### Antes/depois: queda durante concorrencia 200

Ambas as instâncias tinham limite 32 e atraso 100 ms. Iniciei concorrencia 200 no cliente já aberto e matei B após 450 ms. O momento exato muda quantas respostas chegam de cada instância.

Antes:
```text
B morto pelo processo de teste
[CLIENTE] Não foi possível obter estatísticas de srv-50152
[CLIENTE] Enviadas: 200, concluídas: 196, rejeitadas: 0, outras falhas: 4
[CLIENTE] IDs de tarefa repetidos por instância: 0
[CLIENTE] Soma concluídas nas instâncias (delta): 22
```

Depois:
```text
B morto pelo processo de teste
[CLIENTE] Não foi possível obter estatísticas de srv-50152
[CLIENTE] Comparação inconclusiva: faltam estatísticas ou houve mudança/reinício de instância. Delta indisponível (-1).
[CLIENTE] Enviadas: 200, concluídas: 200, rejeitadas: 0, outras falhas: 0
[CLIENTE] IDs de tarefa repetidos por instância: 0
[CLIENTE] Soma concluídas nas instâncias (delta): indisponível - INCONCLUSIVO (instância sem amostra comparável)
```

Depois da queda, o mesmo cliente continuou atendendo seis pedidos pela instância restante:

```text
COMANDO CLIENTE: rr 6
[CLIENTE] > [CLIENTE] srv-50152 pulado (erro: UNAVAILABLE)
[CLIENTE] Tarefa #255 respondida por srv-50151: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] Tarefa #256 respondida por srv-50151: 1! = 1 (0 ms)
[CLIENTE] [CLIENTE] srv-50152 pulado (erro: UNAVAILABLE)
[CLIENTE] Tarefa #257 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] Tarefa #258 respondida por srv-50151: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
[CLIENTE] [CLIENTE] srv-50152 pulado (erro: UNAVAILABLE)
[CLIENTE] Tarefa #259 respondida por srv-50151: 4! = 24 (0 ms)
[CLIENTE] Tarefa #260 respondida por srv-50151: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
```

### Encerramento normal e queda do nomes

```text
COMANDO B2: sair
[NOMES] [NOMES] Removida: srv-50152
COMANDO CLIENTE: atualizar
[CLIENTE] > Instâncias encontradas no serviço de nomes: 1
[CLIENTE]   srv-50151  localhost:50151
COMANDO CLIENTE: atualizar
[CLIENTE] > Instâncias encontradas no serviço de nomes: 2
[CLIENTE]   srv-50151  localhost:50151
[CLIENTE]   srv-50152  localhost:50152
```

O comando sair permitiu desregistro e término normal do processo, incluindo o hook. Não automatizei o gesto Ctrl+C no console do Windows; o caminho do hook também foi revisado no código. Morte forçada não executa esse caminho de forma garantida.

```text
COMANDO CLIENTE: atualizar
[CLIENTE] > Serviço de nomes indisponível; lista anterior mantida. Use atualizar para tentar novamente.
COMANDO CLIENTE: rr 2
[CLIENTE] > Tarefa #321 respondida por srv-50151: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] Tarefa #1 respondida por srv-50152: 1! = 1 (0 ms)
```

O nomes foi morto só depois da demonstração de desregistro. Por isso, na limpeza final, os servidores avisaram que não conseguiram desregistrar e mesmo assim encerraram.

### Limites de entrada preservados

```text
CALCULO 0: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=1, resultado=0! = 1, duracaoMs=0, erro=, codigoErro=]
CALCULO 5000: ResultadoTarefa[ok=true, instancia=srv-50151, idTarefa=2, resultado=5000! = 42285779266055435222... (16326 dígitos), duracaoMs=34, erro=, codigoErro=]
CALCULO -1: ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
CALCULO 5001: ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Entrada inválida: confira os dados informados., codigoErro=INVALID_ARGUMENT]
HASH tamanho=1000000: ResultadoTarefa[ok=true, instancia=srv-50152, idTarefa=2, resultado=cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0, duracaoMs=13, erro=, codigoErro=]
HASH tamanho=5000000: ResultadoTarefa[ok=false, instancia=, idTarefa=0, resultado=, duracaoMs=0, erro=Servidor sem capacidade; tente novamente., codigoErro=RESOURCE_EXHAUSTED]
```

O log do transporte identificou o limite antes de executar a regra de negócio:

```text
[A] io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED: gRPC message exceeds maximum size 4194304: 5000014
```

Não acrescentei limite arbitrário para texto. RESOURCE_EXHAUSTED pode vir do transporte por mensagem grande ou do serviço por falta de vaga; a mensagem genérica do cliente não distingue essas origens. Valores finitos muito grandes em listas continuam sujeitos à precisão e ao overflow de double, como limitação numérica do resumo.

## Referências finais e resultado da revisão

| Correção ou requisito | Referência final | Resultado |
|---|---|---|
| Lista publicada por versão, endereço e descarte de canais | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:75` | Corrigido; sondas de endereço, fechamento, intercalação e stress. |
| Reserva de canais | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:124` | Sem rede no lock; reserva cobre Health, tarefa e consultas. |
| Liberação de canais | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:132` | Fecha fora do lock, após o último uso; terminação verificada. |
| Rodízio e Health manual, PDF 2 e 3.5 | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:154` | Varredura completa da cópia, Health 2 s fora do lock. |
| Retry e deadline, PDF 4 | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:189` | Tarefa 5 s; retry não repete a instância anterior. |
| Fechamento sem reabertura | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:310` | Atualização posterior não recria canais. |
| Comparação em concorrência, PDF 6 | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java:267` | Amostras por instância; falta/recuo de contador torna delta indisponível. |
| Mensagem de medição | `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCloudForge.java:59` | INCONCLUSIVO para delta -1; contrato da GUI preservado. |
| Entradas numéricas | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/CloudForgeServiceImpl.java:265` | Campos vazios e não finitos rejeitados com INVALID_ARGUMENT. |
| Configurações | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/CloudForgeServiceImpl.java:66` | Atraso não negativo e limite positivo. |
| Estado local protegido, PDF 3.2 | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/CloudForgeServiceImpl.java:76` | Admissão e conclusão sob lock; sleep, cálculo e RPC global fora. |
| Estatísticas consistentes | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/CloudForgeServiceImpl.java:193` | Contadores lidos no mesmo lock; atraso e limite são volatile. |
| Registro e Health padrão, PDF 3.1, 3.3 e 3.4 | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/ServidorCloudForge.java:29` | Duas instâncias registradas, grpc-services e HealthStatusManager. |
| Ordem do encerramento | `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/ServidorCloudForge.java:100` | NOT_SERVING antes de desregistrar; hook com espera limitada e limpeza. |
| Nomes e estado global opcional | `ServicoNomes/src/main/java/com/mycompany/servicodenomes/NomesServiceImpl.java:83` | RPC de resposta fora do monitor; histórico central protegido, sem transação. |

Os requisitos obrigatórios das seções 2, 3 e 4 estão atendidos na execução verificada, e todos os cenários pedidos da seção 6 foram executados com processos reais. A GUI não foi editada nem validada visualmente nesta revisão. Não há framework novo, alteração de proto, commit ou push feito por este worker.

Limites que continuam explícitos: nomes e histórico são voláteis e não se recuperam automaticamente; retry não garante execução única; a medição pressupõe ausência de tráfego de outros clientes e de reinício com o mesmo ID; Ctrl+C manual não foi reproduzido. O bônus de histórico global tem evidências anteriores em EVIDENCIAS.md, foi revisado estaticamente aqui e não foi novamente ensaiado com --global nesta revisão. Não ficaram correções obrigatórias pendentes dos achados reproduzidos.

Sobre o transporte, confirmei a distinção entre encerrar o canal e permitir novas chamadas na [API oficial ManagedChannel da versão 1.75.0](https://github.com/grpc/grpc-java/blob/v1.75.0/api/src/main/java/io/grpc/ManagedChannel.java), além da consulta Context7 para /grpc/grpc-java. Essa distinção explica por que apenas chamar shutdown não protegia o intervalo entre Health e ExecutarTarefa.

### Identificação das versões testadas

SHA-256 dos quatro arquivos de produção modificados, na versão final que foi compilada em `depois`:

- `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCore.java`: `1ed6c4b853488128250b719e1ee6e9bc769e03ba7f057696f20564479a91fc76`
- `ClienteCloudForge/src/main/java/com/mycompany/clientecloudforge/ClienteCloudForge.java`: `df6f744fff910eeedc0faae8530d240c50b8444b363ff2acbc50167f6931dcdb`
- `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/CloudForgeServiceImpl.java`: `eda55ec19646d751cbadb3c254ace71120c48dd61c99ed10cd90339ff79bd445`
- `ServidorCloudForge/src/main/java/com/mycompany/servidorcloudforge/ServidorCloudForge.java`: `2ac1f4a515de46180120704493592d46235404998e9e2fcade02d8025814a329`
