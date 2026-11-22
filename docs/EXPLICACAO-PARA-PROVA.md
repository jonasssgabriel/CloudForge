# Explicação para a prova (11/12)

Perguntas que a professora pode fazer, com resposta curta e onde está no código.

**1. Por que `synchronized` só no avanço do índice?**
O índice do rodízio é estado compartilhado: várias threads pedem "o próximo" ao mesmo tempo. Sem lock, duas pegariam o mesmo valor. A região crítica é só ler o índice e somar 1, então é curta e sem rede.
Onde: `ClienteCore.avancarIndice`.

**2. Por que o Health Check fica fora do lock?**
Health.Check é chamada de rede e pode demorar até 2 s. Dentro do lock, todas as requisições do cliente esperariam uma pela outra e seriam atendidas uma de cada vez.
Onde: `ClienteCore.proximoServidorSaudavel`.

**3. Por que criar o canal uma única vez?**
Abrir canal gasta conexão e tempo. O `ManagedChannel` foi feito para ser reaproveitado por várias chamadas. Cada servidor tem canal e stubs guardados e o `atualizar` só cria os novos e fecha os que sumiram.
Onde: `ClienteCore.Servidor` (construtor) e `ClienteCore.atualizar`.

**4. Qual a diferença entre nomes, health e round-robin?**
Nomes diz ONDE estão os servidores. Health diz QUAIS estão saudáveis. Round-robin decide PARA QUAL vai cada chamada. O nomes não faz health check nem balanceamento.
Onde: `NomesServiceImpl.listar`, `ServidorCloudForge.definirHealth`, `ClienteCore.proximoServidorSaudavel`.

**5. DEADLINE_EXCEEDED, UNAVAILABLE e RESOURCE_EXHAUSTED?**
- `DEADLINE_EXCEEDED`: o prazo do cliente (5 s na tarefa, 2 s no health) acabou antes da resposta. O servidor pode estar lento ou parado.
- `UNAVAILABLE`: não deu para falar com o servidor (caiu, porta fechada, conexão recusada).
- `RESOURCE_EXHAUSTED`: o servidor está no ar, mas já atingiu o limite de tarefas simultâneas e recusou.
No cliente os três têm mensagem própria e uma nova tentativa no próximo servidor. `INVALID_ARGUMENT` (entrada ruim) não tenta de novo.
Onde: `ClienteCore.enviar` e `mensagemErro`; `CloudForgeServiceImpl.executarTarefa`.

**6. Por que `synchronized` não resolve entre instâncias?**
Cada instância é um processo com a sua memória e o seu lock. O `synchronized` só coordena threads do mesmo processo. Por isso duas instâncias geram, cada uma, o seu `#1`.
Onde: `CloudForgeServiceImpl` (campos `lock` e `proximoIdTarefa`); teste 9 do README.

**7. Por que a instância que cai continua na lista do nomes?**
Decisão de simplicidade: o nomes só guarda onde cada servidor registrou. Se o processo morre sem desregistrar, ele continua na lista. Quem trata isso é o cliente: o Health.Check falha e a instância é pulada. Em `sair` e Ctrl+C o servidor desregistra antes.
Onde: `NomesServiceImpl.listar`, `ServidorCloudForge.encerrar`, `ClienteCore.proximoServidorSaudavel`.

**8. Limite de simultâneas é a mesma coisa que fila?**
Não. Aqui, passando do limite o servidor rejeita na hora com `RESOURCE_EXHAUSTED` e conta em `rejeitadas`. Não guarda o pedido. Quem decide tentar outra instância é o cliente. Fila seria o servidor guardar o pedido e atender depois.
Onde: `CloudForgeServiceImpl.executarTarefa` (bloco que compara `emAndamento < limite`).

**9. Por que não segurar o lock durante o processamento?**
Se o fatorial ou o `sleep` ficassem dentro do `synchronized`, só uma tarefa rodaria por vez e o limite de simultâneas perderia o sentido. O lock só protege a atualização do estado (contadores, histórico, `emAndamento`), que é rápida.
Onde: `CloudForgeServiceImpl.executarTarefa` (o `processar` fica entre dois blocos `synchronized`).

**10. O que é idempotência e por que o retry é seguro?**
Operação idempotente pode ser repetida sem mudar o resultado. Fatorial, estatística e SHA-256 dão a mesma resposta se repetidos. O efeito colateral (contador e histórico) não importa para o resultado. Por isso o cliente tenta 1 vez no próximo servidor. Cuidado: se a primeira tentativa estourou o deadline, o servidor pode ter concluído mesmo assim e a tarefa aparecer duas vezes no histórico. Isso é aceitável aqui, mas não seria para algo como cobrança. É o mesmo ponto do trabalho das senhas.
Onde: `ClienteCore.enviar`.

**11. O que é condição de corrida e como o `CorridaDemo` mostra?**
É quando o resultado depende da ordem em que as threads rodam. `contador++` são três passos (ler, somar, gravar). Duas threads podem ler o mesmo valor e uma atualização se perde. O `CorridaDemo` roda 8 threads x 100000 incrementos: esperado 800000. Sem `synchronized` deu entre 127027 e 150818, e com `synchronized` deu 800000.
Onde: `CorridaDemo.main`.

**12. Como o `concorrencia 200` prova que está protegido?**
Dispara 200 tarefas em threads, soma as concluídas, compara com o aumento de `concluidas` nas instâncias (`ObterEstatisticas`) e conta ids repetidos por instância (tem que dar 0).
Onde: `ClienteCore.concorrencia` e `ClienteCore.enviar` (conjunto `idsRespondidos`).

**13. O que muda no bônus?**
Com `--global`, cada servidor manda o registro ao `ServicoNomes`, que guarda um histórico único protegido por `synchronized`. Dois níveis de sincronização:
- **Threads (mesmo processo):** `synchronized` resolve, porque a memória é a mesma.
- **Processos (instâncias diferentes):** não há memória comum. É preciso um serviço central ou outro mecanismo, via chamada de rede.
O que fica de fora: (a) tolerância a falha do serviço central (se o nomes cair, o histórico global para; por isso o servidor só loga o aviso e segue); (b) consistência (o registro vai depois de concluir a tarefa, sem transação, então pode haver registro perdido ou atraso). E o contador `#id` de cada instância continua local.
Onde: `CloudForgeServiceImpl.executarTarefa` (bloco `global`), `NomesServiceImpl.registrarProcessamento` e `obterHistoricoGlobal`, `ClienteCloudForge.mostrarGlobal`.

**14. Por que o health marca `""` e o nome do serviço?**
O `""` é o status geral do servidor e o outro é o do serviço específico. O cliente consulta `""`. Marcamos os dois para ficar coerente.
Onde: `ServidorCloudForge.definirHealth`.

**15. E se o cliente desiste no meio da tarefa?**
O servidor espera em fatias de 200 ms e checa `Context.current().isCancelled()`. Se cancelou, devolve `CANCELLED` e o `finally` libera a vaga, então o limite não vaza.
Onde: `CloudForgeServiceImpl.executarTarefa`.
