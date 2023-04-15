defmodule ProcessSpec do
  def spawn_client(reply_time) do
    {:ok, spawn_link(__MODULE__, :client, [reply_time])}
  end

  def client(reply_time) do
    receive do
      {"Hello", pid, data} ->
        Process.sleep(reply_time)
        send(pid, {"Done", self(), data})
        client(reply_time)
      _ ->
        IO.puts("Unexpected message received!")
    end
  end

  def spawn_server(counter, data, reply_time) do
    pid = spawn_link(__MODULE__, :server, [counter, spawn_link(__MODULE__, :client, [reply_time]), 0, data])
    send(pid, "Start")
    {:ok, pid}
  end

  def server(counter, clients, message_count, data) do
    receive do
      "Start" ->
        send(clients, {"Hello", self(), data})
        server(counter, clients, message_count, data)
      {"Done", pid, data} ->
        send(counter, 1)
        send(pid, {"Hello", self(), data})
        server(counter, clients, message_count+1, data)
      "Stop" ->
        exit(:kill)
      _ ->
        IO.puts("Unexpected message received!")
    end
  end
  def spawn_counter(interval, summation_mode) do
    pid = spawn_link(__MODULE__, :aggregator, [0, :erlang.system_time(:millisecond), [], summation_mode])
    Process.register(pid, :counter)
    if summation_mode != :avg_total do
      spawn_link(__MODULE__, :logger, [pid, interval])
    end
    {:ok, pid}
  end
  def aggregator(n, start_time, record, mode) do

    receive do
      "Start" ->
        aggregator(0, :erlang.system_time(:millisecond), [], mode)
      "Update" ->
        elapsed_time = (:erlang.system_time(:millisecond)-start_time)/1000
        aggregator(0, :erlang.system_time(:millisecond), [n/elapsed_time | record], mode)
      "Stop" ->
        {:ok, file} = File.open("r_400_500_ta_23.txt", [:append])
        case mode do
          :avg ->
            average = :lists.sum(record) / length(record)
            :io.format(file, "~.3f~n", [average])
          :full ->
            for n <- :lists.reverse(record) do
              :io.format(file, "~.2f~n", [n])
            end
          :avg_total ->
            :io.format(file, "~.3f~n", [(n*1000)/(:erlang.system_time(:millisecond)-start_time)])
        end
      messages_count ->
        aggregator(n+messages_count, start_time, record, mode)
    end
  end

  def logger(counter, interval) do
    Process.sleep(interval)
    send(counter,"Update")
    logger(counter, interval)
  end
end
