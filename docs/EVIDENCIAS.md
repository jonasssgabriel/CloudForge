# Evidências dos testes (saída real)

Tudo abaixo foi executado de verdade em 2026-11-22, com nomes, servidores e cliente em processos separados (JDK 26, gRPC 1.75.0). Os trechos são cópia da saída, sem as linhas `WARNING` do JDK e sem o cabeçalho de comandos do cliente.

## Teste 1: nomes + 2 instâncias (portas 50051 e 50052)

```
[NOMES] Serviço de nomes ouvindo na porta 50050
[NOMES] Registrada: srv-50051 em localhost:50051
[NOMES] Registrada: srv-50052 em localhost:50052
--- srv-50051
[srv-50051] no ar na porta 50051 (limite=4, global=false). Digite 'ajuda' para os comandos.
[srv-50051] registrado em localhost:50050
```

## Testes 2, 3 e 9: lista vinda do nomes, round-robin e estado local (as duas instâncias geram #1)

```
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Tarefa #1 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
Tarefa #1 respondida por srv-50052: 1! = 1 (1 ms)
Tarefa #2 respondida por srv-50051: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (3 ms)
Tarefa #2 respondida por srv-50052: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
Tarefa #3 respondida por srv-50051: 4! = 24 (1 ms)
Tarefa #3 respondida por srv-50052: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (2 ms)
> srv-50051: recebidas=3 concluídas=3 rejeitadas=0 em andamento=0 limite=4
srv-50052: recebidas=3 concluídas=3 rejeitadas=0 em andamento=0 limite=4
>
```

## Teste 4: NOT_SERVING e SERVING

```
## com srv-50052 NOT_SERVING
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Tarefa #4 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] srv-50052 pulado (NOT_SERVING)
Tarefa #5 respondida por srv-50051: 1! = 1 (0 ms)
[CLIENTE] srv-50052 pulado (NOT_SERVING)
Tarefa #6 respondida por srv-50051: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] srv-50052 pulado (NOT_SERVING)
Tarefa #7 respondida por srv-50051: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
> 
## depois de serving
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Tarefa #8 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
Tarefa #4 respondida por srv-50052: 1! = 1 (0 ms)
Tarefa #9 respondida por srv-50051: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
Tarefa #5 respondida por srv-50052: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
>
```

## Teste 5: queda de uma instância com o cliente aberto

```
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Tarefa #10 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
Tarefa #6 respondida por srv-50052: 1! = 1 (0 ms)
> Tarefa #11 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Tarefa #12 respondida por srv-50051: 1! = 1 (0 ms)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Tarefa #13 respondida por srv-50051: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (0 ms)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Tarefa #14 respondida por srv-50051: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
> [CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Tarefa #15 respondida por srv-50051: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Tarefa #16 respondida por srv-50051: 1! = 1 (0 ms)
>
```

## Teste 6: nenhum servidor saudável

```
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> [CLIENTE] srv-50051 pulado (NOT_SERVING)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
[CLIENTE] srv-50051 pulado (NOT_SERVING)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Nenhum servidor saudável no momento. Tente novamente.
> [CLIENTE] srv-50051 pulado (NOT_SERVING)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
[CLIENTE] srv-50051 pulado (NOT_SERVING)
[CLIENTE] srv-50052 pulado (erro: UNAVAILABLE)
Nenhum servidor saudável no momento. Tente novamente.
>
```

## Teste 7: deadline (atraso 7000 ms maior que 5 s)

```
Instâncias encontradas no serviço de nomes: 2
  srv-50052  localhost:50052
  srv-50051  localhost:50051
> Tarefa #1 respondida por srv-50052: 10! = 3628800 (1 ms)
> [CLIENTE] srv-50051 DEADLINE_EXCEEDED (Tempo limite excedido; tente novamente.) Tentando o próximo servidor saudável.
Tarefa #2 respondida por srv-50052: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
[CLIENTE] srv-50051 DEADLINE_EXCEEDED (Tempo limite excedido; tente novamente.) Tentando o próximo servidor saudável.
Tarefa #3 respondida por srv-50052: 1! = 1 (0 ms)
>
```

## Teste 8a: concorrência 200, limite 4

```
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Enviadas: 200, concluídas: 200, rejeitadas: 0, outras falhas: 0
IDs de tarefa repetidos por instância: 0
Soma concluídas nas instâncias (delta): 200 — OK
>
```

## Teste 8b: concorrência 200, limite 2 (RESOURCE_EXHAUSTED)

```
Instâncias encontradas no serviço de nomes: 2
  srv-50051  localhost:50051
  srv-50052  localhost:50052
> Enviadas: 200, concluídas: 193, rejeitadas: 7, outras falhas: 0
IDs de tarefa repetidos por instância: 0
Soma concluídas nas instâncias (delta): 193 — OK
> srv-50051: recebidas=217 concluídas=214 rejeitadas=28 em andamento=0 limite=2
srv-50052: recebidas=198 concluídas=198 rejeitadas=31 em andamento=0 limite=2
>
```

## Extra: validação de entrada (INVALID_ARGUMENT)

```
Instâncias encontradas no serviço de nomes: 2
  srv-50052  localhost:50052
  srv-50051  localhost:50051
> Entrada inválida: confira os dados informados.
> Entrada inválida: confira os dados informados.
> Entrada inválida: confira os dados informados.
> Uso: enviar <CALCULO|DADOS|HASH> <entrada>
>
```

## Extra: fatorial de 5000 com BigInteger

```
Instâncias encontradas no serviço de nomes: 2
  srv-50052  localhost:50052
  srv-50051  localhost:50051
> Tarefa #4 respondida por srv-50052: 5000! = 42285779266055435222... (16326 dígitos) (40 ms)
>
```

## Bônus: histórico global compartilhado (--global)

```
Instâncias encontradas no serviço de nomes: 2
  srv-50052  localhost:50052
  srv-50051  localhost:50051
> Tarefa #5 respondida por srv-50052: 89f25b17dd5641ea484d921c74a4420e88930d57a1f9b3bc665a2868c4b667af (0 ms)
Tarefa #1 respondida por srv-50051: 1! = 1 (1 ms)
Tarefa #6 respondida por srv-50052: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (2 ms)
Tarefa #2 respondida por srv-50051: ecaa9b15e57b9c46ec1cd72866a99ff3abbdc16ffc9e34ed94feeba26fffcb7c (0 ms)
Tarefa #7 respondida por srv-50052: 4! = 24 (0 ms)
Tarefa #3 respondida por srv-50051: soma=6, média=2, mínimo=1, máximo=3, ordenada=[1, 2, 3] (2 ms)
> Total global: 10
srv-50052 #1 CALCULO 01:37:39
srv-50052 #2 HASH 01:37:52
srv-50052 #3 CALCULO 01:37:57
srv-50052 #4 CALCULO 01:38:04
srv-50052 #5 HASH 01:38:07
srv-50051 #1 CALCULO 01:38:07
srv-50052 #6 PROCESSAMENTO_DADOS 01:38:07
srv-50051 #2 HASH 01:38:07
srv-50052 #7 CALCULO 01:38:07
srv-50051 #3 PROCESSAMENTO_DADOS 01:38:07
>
```

## Bônus: log do serviço de nomes

```
[NOMES] Serviço de nomes ouvindo na porta 50050
[NOMES] Registrada: srv-50052 em localhost:50052
[NOMES] Registrada: srv-50051 em localhost:50051
[NOMES] Listar -> 2 instâncias
[NOMES] Histórico global: +1 (srv-50052 #1)
[NOMES] Histórico global: +1 (srv-50052 #2)
...
[NOMES] Histórico global: +1 (srv-50052 #6)
[NOMES] Histórico global: +1 (srv-50051 #2)
[NOMES] Histórico global: +1 (srv-50052 #7)
[NOMES] Histórico global: +1 (srv-50051 #3)
```

## Observações

- No T7 o primeiro envio caiu em srv-50052 pelo rodízio; nos envios seguintes o cliente tentou srv-50051, estourou o deadline de 5 s, tratou o `DEADLINE_EXCEEDED` e usou o próximo servidor saudável. No servidor isso aparece depois como `recebidas - concluidas` (tarefas canceladas).
- No T8b o servidor rejeita (`RESOURCE_EXHAUSTED`) e conta em `rejeitadas`; o cliente tenta uma vez a outra instância, por isso as rejeitadas dos servidores (28 + 31) superam as 7 que o cliente contou como falha final. A soma das concluídas confere (193) e não há ids repetidos.

## Teste da corrida (CorridaDemo)

Comando: `java -cp target\classes com.mycompany.servidorcloudforge.CorridaDemo` (8 threads x 100000 incrementos)

```
Esperado: 800000
SEM synchronized: 127027
COM synchronized: 800000
```

Sem `synchronized` o contador perdeu cerca de 85% das atualizações; com ele o valor é exato. Em outras execuções o valor sem lock varia (ex.: 150818).

## Verificações estáticas

- Nenhuma porta de servidor fixa no cliente: `grep -rnE "5005[1-9]" ClienteCloudForge/src/main/java` não retorna nada. A única porta no código é a do serviço de nomes (`localhost:50050`), o endereço conhecido que o enunciado permite e que pode ser trocado por argumento.
- `synchronized` no servidor só envolve atualização curta de contadores e histórico (`CloudForgeServiceImpl`); o `Thread.sleep` e o processamento ficam fora do lock.
- No cliente o único `synchronized` do rodízio é `avancarIndice` (`ClienteCore`); o `Health.Check` roda fora do lock.

## Repetição final dos testes (22/11/2026)

Depois da revisão do código e da integração da tela (eventos reais, canais com contagem de uso), os três projetos foram recompilados com `scripts -compilar-tudo.bat` e a bateria foi repetida com processos Java reais (nomes 50050, servidores 50051 e 50052, cliente de console). Resultado:

| Teste | Resultado |
|---|---|
| T2/T3 `rr 6` | Alternou entre `srv-50052` e `srv-50051`; as duas geraram `Tarefa #1` (estado local) |
| T4 NOT_SERVING e `serving` | `pulado (NOT_SERVING)` e depois a instância voltou ao rodízio |
| T5 instância encerrada | `pulado (erro: UNAVAILABLE)` e o cliente seguiu com a outra |
| T6 nenhuma saudável | `Nenhum servidor saudável no momento. Tente novamente.` |
| T7 `atraso 7000` | `DEADLINE_EXCEEDED ... Tentando o próximo servidor saudável.` e a tarefa foi refeita na outra instância |
| T8a `concorrencia 200` | Enviadas 200, concluídas 200, rejeitadas 0, ids repetidos 0, soma confere |
| T8b `limite 2` e `concorrencia 200` | Enviadas 200, concluídas 189, rejeitadas 11, ids repetidos 0, soma confere |
| Entradas inválidas | `Entrada inválida: confira os dados informados.` sem tentar outro servidor |
| `CALCULO 5000` | Aceito (BigInteger, 16326 dígitos) |
| Bônus `--global` | `Total global: 10`, com os registros de cada instância |

Na GUI, os mesmos cenários foram conferidos por captura de tela (`docs/img/`): alternância 1, 2, 1, 2; instância em NOT_SERVING; concorrência com 50 tarefas (46 concluídas, 4 rejeitadas, 0 repetidos, "soma confere"); `atraso 6500` com DEADLINE_EXCEEDED em 5014 ms e segunda tentativa na outra instância; instância encerrada de verdade. Também foi verificado que o callback de eventos nunca roda com a trava da lista (`Thread.holdsLock` falso) e que cada par instância + id de tarefa dos eventos existe no histórico real do servidor.
